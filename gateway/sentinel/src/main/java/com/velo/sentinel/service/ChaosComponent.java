package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * ChaosComponent: The Fault Injection Engine.
 * 
 * Injects randomized latency and failures to test system resilience.
 */
@Service
public class ChaosComponent {
    private static final Logger log = LoggerFactory.getLogger(ChaosComponent.class);
    private final Random random = new Random();

    @Value("${velo.sentinel.chaos.enabled:false}")
    private boolean enabled;

    @Value("${velo.sentinel.chaos.failure-rate:0.0}")
    private double failureRate;

    @Value("${velo.sentinel.chaos.latency-rate:0.0}")
    private double latencyRate;

    @Value("${velo.sentinel.chaos.max-latency-ms:500}")
    private int maxLatencyMs;

    /**
     * Potentially injects chaos before an operation.
     * 
     * @param modelName Name of the model (for logging).
     * @throws RuntimeException If a synthetic failure is injected.
     */
    public void maybeInjectChaos(String modelName) {
        if (!enabled) return;

        // 1. Inject Latency
        if (random.nextDouble() < latencyRate) {
            int latency = random.nextInt(maxLatencyMs);
            log.warn("CHAOS-LATENCY: Injecting {}ms delay for model {}", latency, modelName);
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 2. Inject Failure
        if (random.nextDouble() < failureRate) {
            log.error("CHAOS-FAILURE: Injecting synthetic exception for model {}", modelName);
            throw new RuntimeException("CHAOS-INJECTION: Synthetic backend failure");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
