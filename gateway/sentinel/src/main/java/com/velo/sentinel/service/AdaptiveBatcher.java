package com.velo.sentinel.service;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.context.InferenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PreDestroy;

/**
 * AdaptiveBatcher: High-performance request coalescing for GPU efficiency.
 * 
 * This service buffers individual requests into batches to maximize backend throughput.
 * It uses a background Virtual Thread to "drain" the queue based on size or time thresholds.
 * Uses SLA-Aware Earliest Deadline First (EDF) scheduling to prevent priority starvation.
 */
@Service
public class AdaptiveBatcher {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBatcher.class);
    
    private final int maxBatchSize = 16;
    private final long maxWaitMs = 5; // 5ms window for batching
    
    // Disaggregated Queues: Prefill (Compute-Bound) vs Decode (Memory-Bound)
    private final BlockingQueue<InferenceTask> prefillQueue = new PriorityBlockingQueue<>();
    private final BlockingQueue<InferenceTask> decodeQueue = new PriorityBlockingQueue<>();
    
    // Sticky Cache: Track which session belongs to which backend "affinity key"
    private final Map<String, String> sessionAffinity = new ConcurrentHashMap<>();
    
    private final ExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();
    private final Thread prefillThread;
    private final Thread decodeThread;
    private final MeterRegistry meterRegistry;
    private volatile boolean shuttingDown = false;

    public AdaptiveBatcher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Register Backpressure Gauges for HPA Scaling
        meterRegistry.gauge("velo.sentinel.backpressure.prefill_depth", prefillQueue, BlockingQueue::size);
        meterRegistry.gauge("velo.sentinel.backpressure.decode_depth", decodeQueue, BlockingQueue::size);
        meterRegistry.gauge("velo.sentinel.backpressure.affinity_cache_size", sessionAffinity, Map::size);

        // Start the disaggregated background processing loops
        this.prefillThread = Thread.ofVirtual().name("sentinel-prefill-batcher").start(() -> processLoop(prefillQueue, 32, 10, "PREFILL"));
        this.decodeThread = Thread.ofVirtual().name("sentinel-decode-batcher").start(() -> processLoop(decodeQueue, 8, 2, "DECODE"));
    }

    @PreDestroy
    public void shutdown() {
        log.info("SHUTDOWN: AdaptiveBatcher draining queues...");
        this.shuttingDown = true;
        this.prefillThread.interrupt();
        this.decodeThread.interrupt();
        try {
            this.prefillThread.join(TimeUnit.SECONDS.toMillis(5));
            this.decodeThread.join(TimeUnit.SECONDS.toMillis(5));
            log.info("SHUTDOWN: AdaptiveBatcher gracefully terminated.");
        } catch (InterruptedException e) {
            log.warn("SHUTDOWN: Batcher termination interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Submits a single inference request to be batched.
     * 
     * @param value The input value.
     * @param sessionId The session ID.
     * @param modelName The model name.
     * @param priority The priority tier (affects SLA and EDF scheduling).
     * @param batchProcessor The function to execute the batch (usually a gRPC call).
     * @return A CompletableFuture that resolves when the batch is processed.
     */
    public CompletableFuture<Float> submit(float value, String sessionId, String modelName, PriorityTier priority,
                                          boolean isPrefill, Function<List<BatchItem>, List<Float>> batchProcessor) {
        CompletableFuture<Float> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + priority.getSlaMs();
        
        InferenceTask task = new InferenceTask(
            new BatchItem(value, sessionId, modelName, isPrefill), 
            future, batchProcessor, deadline, priority
        );

        if (isPrefill) {
            prefillQueue.add(task);
        } else {
            decodeQueue.add(task);
        }
        
        return future;
    }

    /**
     * Submits a request with default INTERACTIVE priority and auto-detected prefill status.
     */
    public CompletableFuture<Float> submit(float value, String sessionId, String modelName, 
                                          Function<List<BatchItem>, List<Float>> batchProcessor) {
        // Default to prefill if not specified (safest) or use a specialized submit
        return submit(value, sessionId, modelName, PriorityTier.INTERACTIVE, true, batchProcessor);
    }

    private void processLoop(BlockingQueue<InferenceTask> targetQueue, int batchSize, long waitMs, String type) {
        while (!Thread.currentThread().isInterrupted() || (!targetQueue.isEmpty() && shuttingDown)) {
            List<InferenceTask> batch = new ArrayList<>();
            try {
                InferenceTask first = targetQueue.poll(shuttingDown ? 10 : 1000, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (shuttingDown) break;
                    continue;
                }
                
                if (System.currentTimeMillis() > first.deadline()) {
                    first.future().completeExceptionally(new TimeoutException("SLA Violated (Deadline passed)"));
                    continue;
                }
                
                batch.add(first);
                long startTime = System.currentTimeMillis();

                while (batch.size() < batchSize && (System.currentTimeMillis() - startTime) < waitMs) {
                    InferenceTask next = targetQueue.poll(waitMs / 2, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        if (System.currentTimeMillis() > next.deadline()) {
                            next.future().completeExceptionally(new TimeoutException("SLA Violated (Deadline passed)"));
                        } else {
                            batch.add(next);
                        }
                    } else {
                        break;
                    }
                }

                log.debug("BATCHER [{}]: Executing batch of size {}.", type, batch.size());
                executeBatch(batch);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("BATCHER-ERROR [{}]: Failed to process batch.", type, e);
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

    /**
     * Calculates the minimum headroom (ms) across all queues.
     */
    private double calculateSlaHeadroom() {
        InferenceTask pTask = prefillQueue.peek();
        InferenceTask dTask = decodeQueue.peek();
        
        long now = System.currentTimeMillis();
        long pDeadline = (pTask != null) ? pTask.deadline() : now + 10000;
        long dDeadline = (dTask != null) ? dTask.deadline() : now + 10000;
        
        return (double) Math.max(0, Math.min(pDeadline, dDeadline) - now);
    }

    public record BatchItem(float value, String sessionId, String modelName, boolean isPrefill) {}
    
    private record InferenceTask(BatchItem item, CompletableFuture<Float> future, 
                               Function<List<BatchItem>, List<Float>> processor,
                               long deadline, PriorityTier priority) implements Comparable<InferenceTask> {
        @Override
        public int compareTo(InferenceTask other) {
            // Earliest Deadline First (EDF)
            return Long.compare(this.deadline, other.deadline);
        }
    }

    public double getBackpressureFactor() {
        int prefillSize = prefillQueue.size();
        int decodeSize = decodeQueue.size();
        // Weighted backpressure factor: Prefill depth is more expensive
        return (prefillSize * 2.0 + decodeSize) / 10.0;
    }

    public double getConcurrencyScore() {
        // Rough score based on active slots relative to soft limit
        return (double) (prefillQueue.size() + decodeQueue.size()) / 64.0;
    }
}
