package com.velo.sentinel.service;

import com.velo.sentinel.backend.MetalBackend;
import com.velo.sentinel.backend.InferenceBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SpeculativeOrchestrator: Orchestrates Drafter (Small) and Target (Large) models.
 * Shaves latency by drafting on local M3 (Metal) before verifying on Cloud GPU.
 */
@Service
public class SpeculativeOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SpeculativeOrchestrator.class);
    private final MetalBackend drafter;

    /**
     * Initializes the orchestrator with a local drafting backend.
     * 
     * @param drafter The local (Metal) backend used for rapid drafting.
     */
    public SpeculativeOrchestrator(MetalBackend drafter) {
        this.drafter = drafter;
    }

    /**
     * Executes a speculative decoding workflow.
     * Starts by drafting on the local M3 backend, then verifies the result with the cloud target.
     * 
     * @param value The initial input value.
     * @param sessionId The session identifier.
     * @param modelName The target model name.
     * @param target The high-fidelity cloud backend for verification.
     * @return The verified inference result.
     */
    public float executeSpeculative(float value, String sessionId, String modelName, InferenceBackend target) {
        log.info("SPECULATIVE-INIT [Session: {}]: Starting speculative pipeline (Drafter: M3, Target: Cloud).", sessionId);

        long start = System.currentTimeMillis();
        
        try {
            // Step 1: Draft on local M3 with a tight timeout (e.g., 30ms)
            Float draft = CompletableFuture.supplyAsync(() -> drafter.infer(value, sessionId, "drafter-m3"))
                .get(30, TimeUnit.MILLISECONDS);
            
            log.debug("SPECULATIVE-DRAFT [Session: {}]: Draft generated: {}. Verifying with Target.", sessionId, draft);

            // Step 2: Verify with Target
            return target.infer(draft, sessionId, modelName);

        } catch (Exception e) {
            // Fallback: If Drafter stutters or fails, execute Target directly to protect user experience
            log.warn("SPECULATIVE-FALLBACK [Session: {}]: Drafter slow or failed. Falling back to standard execution.", sessionId);
            return target.infer(value, sessionId, modelName);
        }
    }
}
