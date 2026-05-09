package com.velo.sentinel.sdk;

import com.velo.sentinel.sdk.model.InferenceRequest;
import com.velo.sentinel.sdk.model.InferenceResponse;
import com.velo.sentinel.sdk.model.PriorityTier;
import com.velo.sentinel.sdk.model.ModelPrecision;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * ResilientSentinelClient: High-level Java SDK for the Velo-Sentinel Gateway.
 * 
 * This client provides built-in resilience patterns to ensure high availability
 * and low tail latency (P99) for mission-critical AI applications.
 * 
 * Features:
 * <ul>
 *   <li><b>Exponential Backoff</b>: Automatically retries on 429 (Rate Limit) and 503 (Overload) errors.</li>
 *   <li><b>Client-Side Hedging</b>: Reduces tail latency by spawning a redundant request if the primary takes too long.</li>
 *   <li><b>Virtual Thread Compatible</b>: Designed for non-blocking execution in modern JDK environments.</li>
 * </ul>
 */
public class ResilientSentinelClient {
    private static final Logger log = Logger.getLogger(ResilientSentinelClient.class.getName());

    private final String baseUrl;
    private final long hedgingDelayMs;
    private final int maxRetries;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * Constructs a new resilient client.
     * 
     * @param baseUrl The endpoint of the Sentinel Gateway (e.g., "http://localhost:8080").
     * @param hedgingDelayMs Delay in milliseconds before spawning a hedge request. Set to 0 to disable.
     * @param maxRetries Maximum number of retry attempts for failed requests.
     */
    public ResilientSentinelClient(String baseUrl, long hedgingDelayMs, int maxRetries) {
        this.baseUrl = baseUrl;
        this.hedgingDelayMs = hedgingDelayMs;
        this.maxRetries = maxRetries;
    }

    /**
     * Executes an inference request asynchronously.
     * 
     * If hedging is enabled, a redundant request will be triggered if the primary call
     * does not return within the specified {@code hedgingDelayMs}.
     * 
     * @param request The inference request configuration.
     * @return A CompletableFuture containing the first successful response.
     */
    public CompletableFuture<InferenceResponse> inferAsync(InferenceRequest request) {
        CompletableFuture<InferenceResponse> primary = executeWithRetry(request, 0);
        
        if (hedgingDelayMs > 0) {
            CompletableFuture<InferenceResponse> hedge = new CompletableFuture<>();
            scheduler.schedule(() -> {
                if (!primary.isDone()) {
                    log.warning("SDK-HEDGING: Primary took too long. Spawning hedge request.");
                    executeWithRetry(request, 0).whenComplete((res, err) -> {
                        if (err == null) hedge.complete(res);
                        else hedge.completeExceptionally(err);
                    });
                }
            }, hedgingDelayMs, TimeUnit.MILLISECONDS);
            
            return CompletableFuture.anyOf(primary, hedge)
                .thenApply(obj -> (InferenceResponse) obj);
        }

        return primary;
    }

    /**
     * Synchronous blocking call for simpler integrations.
     * 
     * @param request The inference request configuration.
     * @return The inference response.
     * @throws ExecutionException If the request fails.
     * @throws InterruptedException If the execution is interrupted.
     */
    public InferenceResponse infer(InferenceRequest request) throws ExecutionException, InterruptedException {
        return inferAsync(request).get();
    }

    private CompletableFuture<InferenceResponse> executeWithRetry(InferenceRequest request, int attempt) {
        return callGateway(request).handle((res, err) -> {
            if (err == null && res.status() == InferenceResponse.Status.SUCCESS) {
                return CompletableFuture.completedFuture(res);
            }
            
            if (attempt < maxRetries) {
                long delay = (long) Math.pow(2, attempt) * 100; // Exponential backoff
                log.info("SDK-RETRY: Attempt " + attempt + " failed. Retrying in " + delay + "ms...");
                
                CompletableFuture<InferenceResponse> retryFuture = new CompletableFuture<>();
                scheduler.schedule(() -> {
                    executeWithRetry(request, attempt + 1).whenComplete((r, e) -> {
                        if (e == null) retryFuture.complete(r);
                        else retryFuture.completeExceptionally(e);
                    });
                }, delay, TimeUnit.MILLISECONDS);
                return retryFuture;
            }
            
            if (err != null) throw new CompletionException(err);
            return CompletableFuture.completedFuture(res);
        }).thenCompose(f -> f);
    }

    private CompletableFuture<InferenceResponse> callGateway(InferenceRequest request) {
        // NOTE: In a real-world production SDK, this would use a gRPC stub or HttpClient.
        // For the purpose of this architecture demo, we simulate the network hop.
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate network latency
                Thread.sleep(20); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new InferenceResponse(request.sessionId(), request.value() * 1.1f, InferenceResponse.Status.SUCCESS);
        });
    }

    /**
     * Gracefully shuts down the internal thread pool used for hedging and retries.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
