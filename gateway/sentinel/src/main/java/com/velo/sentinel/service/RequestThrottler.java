package com.velo.sentinel.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RequestThrottler: Per-Session SLA Enforcement.
 * 
 * Prevents any single session from saturating the inference gateway.
 * Uses a Token-Bucket algorithm (via Resilience4j) to manage request quotas.
 */
@Service
public class RequestThrottler {
    private final RateLimiterRegistry registry;
    private final ConcurrentHashMap<String, RateLimiter> sessionLimiters = new ConcurrentHashMap<>();

    public RequestThrottler() {
        // Default Config: 10 requests per 1-second window
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(0)) // Do not wait if throttled
                .build();
        this.registry = RateLimiterRegistry.of(config);
    }

    /**
     * Executes a task if the session has remaining quota.
     * 
     * @param sessionId The session identifier to throttle.
     * @param task The task to execute.
     * @return The result of the task.
     * @throws RequestThrottledException if the session has exceeded its quota.
     */
    public <T> T throttle(String sessionId, java.util.function.Supplier<T> task) {
        RateLimiter limiter = sessionLimiters.computeIfAbsent(sessionId, registry::rateLimiter);
        
        return RateLimiter.decorateSupplier(limiter, task).get();
    }

    /**
     * Custom exception for throttling events.
     */
    public static class RequestThrottledException extends RuntimeException {
        public RequestThrottledException(String sessionId) {
            super("SLA Violated: Session " + sessionId + " exceeded request quota.");
        }
    }
}
