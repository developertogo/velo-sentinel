package com.velo.sentinel.backend;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import com.velo.sentinel.nativelib.VeloCoreBridge;
import com.velo.sentinel.nativelib.VeloNativeLibrary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MetalBackend: Local Apple Silicon Inference Engine (Velo-Core).
 * Offloads small/privacy-sensitive tasks to the M3 GPU.
 */
@Service
public class MetalBackend implements InferenceBackend, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MetalBackend.class);

    private final VeloNativeLibrary nativeLib;
    private final String modelName;
    private VeloCoreBridge bridge;

    /**
     * Initializes the Metal backend with the native library loader.
     * 
     * @param nativeLib Loader for the Velo-Core native library.
     * @param modelName The model name to load into GPU memory.
     */
    public MetalBackend(VeloNativeLibrary nativeLib, 
                        @Value("${metal.grpc.model-name:phi-2-metal}") String modelName) {
        this.nativeLib = nativeLib;
        this.modelName = modelName;
    }

    /**
     * Post-construct initialization to load the native engine via the FFM bridge.
     */
    @PostConstruct
    public void init() {
        log.info("METAL-BACKEND: Initializing native bridge for model: {}", modelName);
        try {
            // Initialize the engine with 1 slot and 2048 context tokens for local edge use
            this.bridge = new VeloCoreBridge(nativeLib, modelName, 1, 2048);
            log.info("METAL-BACKEND: Native engine initialized successfully.");
        } catch (Exception e) {
            log.error("METAL-BACKEND: Failed to initialize native engine. Falling back to simulation.", e);
        }
    }

    @Override
    public float infer(float value) {
        return infer(value, "local-session", modelName);
    }

    @Override
    public float infer(float value, String sessionId) {
        return infer(value, sessionId, modelName);
    }

    @Override
    public float infer(float value, String sessionId, String modelName) {
        if (bridge == null) {
            log.warn("METAL-EXECUTION [SIMULATION] [Session: {}]: Bridge not available.", sessionId);
            return value * 1.05f; 
        }

        log.info("METAL-EXECUTION [M3 GPU] [Session: {}]: Executing native-accelerated inference.", sessionId);
        
        // In a real scenario, we'd tokenize 'value' or pass it as a prompt.
        // For this demo, we simulate a small token list and take the first output token as a float.
        List<Integer> result = bridge.generate(List.of((int) value), 1);
        return result.isEmpty() ? value : (float) result.get(0);
    }
    @Override
    public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
        return infer(value, sessionId, modelName);
    }

    @Override
    @PreDestroy
    public void close() {
        if (bridge != null) {
            log.info("METAL-BACKEND: Closing native bridge...");
            bridge.close();
        }
    }
}
