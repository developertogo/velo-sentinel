package com.velo.sentinel.backend;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MetalBackend: Local Apple Silicon Inference Engine (Velo-Core).
 * Offloads small/privacy-sensitive tasks to the M3 GPU.
 */
@Service
public class MetalBackend implements InferenceBackend {
    private static final Logger log = LoggerFactory.getLogger(MetalBackend.class);

    @Override
    public float infer(float value) {
        return infer(value, "local-session", "phi-2-metal");
    }

    @Override
    public float infer(float value, String sessionId) {
        return infer(value, sessionId, "phi-2-metal");
    }

    @Override
    public float infer(float value, String sessionId, String modelName) {
        log.info("METAL-EXECUTION [M3 GPU] [Session: {}]: Executing local-accelerated inference.", sessionId);
        // Simulate Metal acceleration
        return value * 1.05f; 
    }
    @Override
    public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
        return infer(value, sessionId, modelName);
    }
}
