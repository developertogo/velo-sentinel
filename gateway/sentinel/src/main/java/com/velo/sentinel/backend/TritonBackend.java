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
 * TritonBackend: The "Reliable Old Guard."
 *
 * NVIDIA Triton is a very popular and stable system for running AI models.
 * In this project, we treat Triton as our "Ground Truth." This means if we're
 * ever unsure about an answer, we trust Triton's answer the most.
 *
 * This class handles the "Phone Call" (gRPC) to the Triton server and
 * translates the answer it gives back.
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

        // --- TRANSLATION LOGIC ---
        // AI models often speak in "Binary" (zeros and ones, or "Bytes").
        // This code takes those raw bytes and translates them back into a "Float"
        // ( a decimal number like 0.85) that humans and the rest of our code can understand.
        if (response.getRawOutputContentsCount() == 0) {
            throw new RuntimeException("Empty response from Triton");
        }
        byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

        return ByteBuffer.wrap(rawBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat();
    }

    @Override
    public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
        return infer(value, sessionId, modelName);
    }
}
