package com.velo.sentinel.service;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.context.InferenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import jakarta.annotation.PreDestroy;

/**
 * AdaptiveBatcher: The "Elevator Operator" of the Inference System.
 *
 * Imagine you're in a big building (the AI server). People (requests) arrive one by one
 * wanting to go up.
 * - If you send the elevator up for every single person, it's very expensive and slow.
 * - Instead, the elevator waits a few seconds (waitMs) for more people (Batching) to arrive.
 * - This way, you take 8 or 32 people at once, making it much more efficient!
 *
 * This class handles two special types of "Elevators":
 * 1. **Prefill (The Boarding Phase)**: This is when the AI first reads your prompt.
 *    It's like people boarding a train. It's "compute-heavy" (hard work).
 * 2. **Decode (The Traveling Phase)**: This is when the AI generates words one by one.
 *    It's like the train moving between stations. It's "memory-heavy" (less work, but happens a lot).
 *
 * By keeping these separate, we can optimize the hardware for both.
 *
 * We use **Virtual Threads** (from Java 25) which are like "Super-Fast Workers"
 * that can handle thousands of elevators at once without getting tired or using much memory.
 */
@Service
@Primary
public class AdaptiveBatcher {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBatcher.class);

    // Config tuned for NVIDIA H100 / Apple M3 Ultra disaggregated clusters
    private static final int MAX_PREFILL_BATCH_SIZE = 32;
    private static final int MAX_DECODE_BATCH_SIZE  = 8;
    private static final long PREFILL_WAIT_MS       = 10;
    private static final long DECODE_WAIT_MS        = 2;

    // Disaggregated queues: Prefill (compute-bound) vs Decode (memory-bound)
    private final BlockingQueue<InferenceTask> prefillQueue = new PriorityBlockingQueue<>();
    private final BlockingQueue<InferenceTask> decodeQueue  = new PriorityBlockingQueue<>();

    // Sticky Cache: session → backend affinity key
    private final Map<String, String> sessionAffinity = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private volatile boolean shuttingDown = false;

    private final Thread prefillThread;
    private final Thread decodeThread;

    /**
     * Initializes the AdaptiveBatcher with dedicated virtual threads.
     *
     * @param meterRegistry Registry for enqueuing backpressure metrics.
     */
    public AdaptiveBatcher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register backpressure gauges for HPA scaling
        meterRegistry.gauge("velo.sentinel.backpressure.prefill_depth",       prefillQueue,   BlockingQueue::size);
        meterRegistry.gauge("velo.sentinel.backpressure.decode_depth",        decodeQueue,    BlockingQueue::size);
        meterRegistry.gauge("velo.sentinel.backpressure.affinity_cache_size", sessionAffinity, Map::size);

        // Start disaggregated processing loops on dedicated virtual threads
        this.prefillThread = Thread.ofVirtual().name("sentinel-prefill-batcher")
                .start(() -> processLoop("PREFILL", prefillQueue, MAX_PREFILL_BATCH_SIZE, PREFILL_WAIT_MS));
        this.decodeThread  = Thread.ofVirtual().name("sentinel-decode-batcher")
                .start(() -> processLoop("DECODE",  decodeQueue,  MAX_DECODE_BATCH_SIZE,  DECODE_WAIT_MS));
    }

    /**
     * Gracefully shuts down the batcher, allowing in-flight requests to drain.
     */
    @PreDestroy
    public void shutdown() {
        log.info("SHUTDOWN: AdaptiveBatcher draining disaggregated queues...");
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
     * Submits a single request to the batcher.
     *
     * It's like a person stepping into the elevator lobby and getting a ticket (Future).
     *
     * @param value          The data to process.
     * @param sessionId      Who is asking? (Used to keep people from the same group together).
     * @param modelName      Which AI should answer?
     * @param priority       How fast does this person need to get there? (SLA).
     * @param isPrefill      Is this a "Boarding" request or a "Traveling" request?
     * @param batchProcessor The "Elevator" itself (the logic that actually runs the AI).
     * @return A "Ticket" (CompletableFuture) that will eventually contain the answer.
     */
    public CompletableFuture<Float> submit(
            float value,
            String sessionId,
            String modelName,
            PriorityTier priority,
            boolean isPrefill,
            Function<List<BatchItem>, List<Float>> batchProcessor) {

        CompletableFuture<Float> future = new CompletableFuture<>();
        PriorityTier effectivePriority = (priority != null) ? priority : PriorityTier.INTERACTIVE;
        long deadline = System.currentTimeMillis() + effectivePriority.getSlaMs();

        InferenceTask task = new InferenceTask(
                new BatchItem(value, sessionId, modelName, isPrefill),
                future, batchProcessor, deadline, effectivePriority);

        if (isPrefill) {
            prefillQueue.add(task);
        } else {
            decodeQueue.add(task);
        }
        return future;
    }

    /**
     * Convenience overload: defaults to {@link PriorityTier#INTERACTIVE} and prefill routing.
     *
     * @param value          The input embedding value.
     * @param sessionId      The session ID.
     * @param modelName      The model name.
     * @param batchProcessor The function to execute the batch.
     * @return A future resolving to the inference result.
     */
    public CompletableFuture<Float> submit(
            float value,
            String sessionId,
            String modelName,
            Function<List<BatchItem>, List<Float>> batchProcessor) {
        return submit(value, sessionId, modelName, PriorityTier.INTERACTIVE, true, batchProcessor);
    }

    // -------------------------------------------------------------------------
    // Internal processing loop
    // -------------------------------------------------------------------------

    /**
     * The internal loop that actually manages the "Elevator."
     *
     * 1. It waits for the first person (poll).
     * 2. Once someone arrives, it starts a timer (waitMs).
     * 3. It tries to fill the elevator with more people until it's full (maxBatchSize)
     *    or the timer runs out.
     * 4. Then it tells the AI to process everyone at once (executeBatch).
     */
    private void processLoop(String type, BlockingQueue<InferenceTask> queue, int maxBatchSize, long waitMs) {
        while (!Thread.currentThread().isInterrupted() || (!queue.isEmpty() && shuttingDown)) {
            List<InferenceTask> batch = new ArrayList<>();
            try {
                InferenceTask first = queue.poll(shuttingDown ? 10 : 1000, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (shuttingDown) break;
                    continue;
                }

                if (System.currentTimeMillis() > first.deadline()) {
                    first.future().completeExceptionally(
                            new TimeoutException("SLA Violated in " + type));
                    continue;
                }

                batch.add(first);
                long startTime = System.currentTimeMillis();

                while (batch.size() < maxBatchSize
                        && (System.currentTimeMillis() - startTime) < waitMs) {
                    InferenceTask next = queue.poll(waitMs / 2, TimeUnit.MILLISECONDS);
                    if (next == null) break;
                    if (System.currentTimeMillis() > next.deadline()) {
                        next.future().completeExceptionally(
                                new TimeoutException("SLA Violated in " + type));
                    } else {
                        batch.add(next);
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

        var processor = tasks.get(0).processor();
        List<BatchItem> items = tasks.stream().map(InferenceTask::item).toList();

        try {
            List<Float> results = processor.apply(items);
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).future().complete(results.get(i));
            }
        } catch (Exception e) {
            tasks.forEach(t -> t.future().completeExceptionally(e));
        }
    }

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------

    /**
     * Weighted backpressure factor: prefill depth counts 2× (more compute-expensive).
     * Consumed by the Sentinel HPA adapter and Grafana dashboards.
     *
     * @return A calculated score representing current load pressure.
     */
    public double getBackpressureFactor() {
        return (prefillQueue.size() * 2.0 + decodeQueue.size()) / 10.0;
    }

    /**
     * Rough concurrency score relative to a soft limit of 64 in-flight tasks.
     *
     * @return Percentage of concurrency headroom utilized.
     */
    public double getConcurrencyScore() {
        return (double) (prefillQueue.size() + decodeQueue.size()) / 64.0;
    }

    /**
     * Minimum SLA headroom (ms) across both queues — used for proactive back-pressure.
     *
     * @return Time in milliseconds until the most urgent task violates its SLA.
     */
    public double calculateSlaHeadroom() {
        InferenceTask pTask = prefillQueue.peek();
        InferenceTask dTask = decodeQueue.peek();
        long now = System.currentTimeMillis();
        long pDeadline = (pTask != null) ? pTask.deadline() : now + 10_000;
        long dDeadline = (dTask != null) ? dTask.deadline() : now + 10_000;
        return (double) Math.max(0, Math.min(pDeadline, dDeadline) - now);
    }

    // -------------------------------------------------------------------------
    // Data records
    // -------------------------------------------------------------------------

    /**
     * BatchItem: Represents a single unit of work within a coalesced batch.
     *
     * @param value The input data.
     * @param sessionId The session identifier.
     * @param modelName The target model.
     * @param isPrefill Whether this item belongs to the prefill phase.
     */
    public record BatchItem(float value, String sessionId, String modelName, boolean isPrefill) {}

    private record InferenceTask(
            BatchItem item,
            CompletableFuture<Float> future,
            Function<List<BatchItem>, List<Float>> processor,
            long deadline,
            PriorityTier priority) implements Comparable<InferenceTask> {

        @Override
        public int compareTo(InferenceTask other) {
            return Long.compare(this.deadline, other.deadline);
        }
    }
}
