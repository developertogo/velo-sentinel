package com.velo.sentinel.backend;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.context.InferenceContext;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TritonBackend: The Legacy Inference Engine (Ground Truth).
 * Maintains backwards compatibility with established NVIDIA Triton deployments.
 */
@Service
public class TritonBackend implements InferenceBackend {

    private static final Logger log = LoggerFactory.getLogger(TritonBackend.class);
    private final TritonGrpcClient tritonClient;

    public TritonBackend(TritonGrpcClient tritonClient) {
        this.tritonClient = tritonClient;
    }

    /**
     * Executes legacy inference for callers without explicit session context.
     */
    @Override
    public float infer(float value) {
        String activeSession = InferenceContext.SESSION_ID.isBound()
                ? InferenceContext.SESSION_ID.get()
                : "legacy-session";
        return infer(value, activeSession);
    }

    @Override
    public float infer(float value, String sessionId) {
        return infer(value, sessionId, "simple");
    }

    /**
     * Executes legacy inference with session-aware logging for a specific model.
     * Extracts and converts raw binary output from Triton tensors.
     */
    @Override
    public float infer(float value, String sessionId, String modelName) {
        log.debug("TRITON-EXECUTION [Session: {}, Model: {}]: Requesting ground truth.", sessionId, modelName);

        // Call Triton via the hardened gRPC client
        ModelInferResponse response = tritonClient.infer(value, modelName);

        // Extraction Logic: Triton returns results in a raw binary buffer
        if (response.getRawOutputContentsCount() == 0) {
            log.error("TRITON-ERROR: Response contains no output tensors.");
            throw new RuntimeException("Empty response from Triton");
        }

        byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

        // Convert bytes → float
        return ByteBuffer.wrap(rawBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat();
    }

    @Override
    public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
        return infer(value, sessionId, modelName);
    }
}
