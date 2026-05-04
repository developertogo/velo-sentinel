package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * AdaptiveBatcher: High-performance request coalescing for GPU efficiency.
 * 
 * This service buffers individual requests into batches to maximize backend throughput.
 * It uses a background Virtual Thread to "drain" the queue based on size or time thresholds.
 */
@Service
public class AdaptiveBatcher {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBatcher.class);
    
    private final int maxBatchSize = 16;
    private final long maxWaitMs = 5; // 5ms window for batching
    
    private final BlockingQueue<InferenceTask> queue = new LinkedBlockingQueue<>();
    private final ExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();

    public AdaptiveBatcher() {
        // Start the background batch processing loop
        Thread.ofVirtual().name("sentinel-batcher").start(this::processLoop);
    }

    /**
     * Submits a single inference request to be batched.
     * Returns a CompletableFuture that resolves when the batch is processed.
     */
    public CompletableFuture<Float> submit(float value, String sessionId, String modelName, 
                                          Function<List<BatchItem>, List<Float>> batchProcessor) {
        CompletableFuture<Float> future = new CompletableFuture<>();
        queue.add(new InferenceTask(new BatchItem(value, sessionId, modelName), future, batchProcessor));
        return future;
    }

    private void processLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<InferenceTask> batch = new ArrayList<>();
            try {
                // Wait for the first item
                InferenceTask first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                
                batch.add(first);
                long startTime = System.currentTimeMillis();

                // Try to fill the batch until maxBatchSize or maxWaitMs is reached
                while (batch.size() < maxBatchSize && (System.currentTimeMillis() - startTime) < maxWaitMs) {
                    InferenceTask next = queue.poll(maxWaitMs / 2, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        batch.add(next);
                    } else {
                        break;
                    }
                }

                log.debug("BATCHER: Executing batch of size {}.", batch.size());
                executeBatch(batch);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("BATCHER-ERROR: Failed to process batch.", e);
            }
        }
    }

    private void executeBatch(List<InferenceTask> tasks) {
        if (tasks.isEmpty()) return;

        // All tasks in a batch must share the same processor (logic-wise)
        // We use the processor from the first task
        var processor = tasks.get(0).processor();
        List<BatchItem> items = tasks.stream().map(InferenceTask::item).toList();

        try {
            List<Float> results = processor.apply(items);
            for (int i = 0; i < tasks.size(); i++) {
                if (i < results.size()) {
                    tasks.get(i).future().complete(results.get(i));
                } else {
                    tasks.get(i).future().completeExceptionally(new RuntimeException("Incomplete batch result"));
                }
            }
        } catch (Exception e) {
            tasks.forEach(t -> t.future().completeExceptionally(e));
        }
    }

    public record BatchItem(float value, String sessionId, String modelName) {}
    private record InferenceTask(BatchItem item, CompletableFuture<Float> future, 
                               Function<List<BatchItem>, List<Float>> processor) {}
}
