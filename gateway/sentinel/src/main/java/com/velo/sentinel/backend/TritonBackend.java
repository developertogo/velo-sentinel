package com.velo.sentinel.backend;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.context.InferenceContext;

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

    /**
     * Executes legacy inference with session-aware logging.
     * Extracts and converts raw binary output from Triton tensors.
     */
    @Override
    public float infer(float value, String sessionId) {
        log.debug("TRITON-EXECUTION [Session: {}]: Requesting ground truth from legacy backend.", sessionId);

        // Call Triton via the hardened gRPC client
        ModelInferResponse response = tritonClient.infer(value);

        // Extraction Logic: Triton returns results in a raw binary buffer
        // We expect a single FP32 value (4 bytes) in Little Endian format
        if (response.getRawOutputContentsCount() == 0) {
            log.error("TRITON-ERROR: Response contains no output tensors.");
            throw new RuntimeException("Empty response from Triton");
        }

        byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

        // Convert bytes → float (Standard NVIDIA/Cuda byte order)
        return ByteBuffer.wrap(rawBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat();
    }
}
