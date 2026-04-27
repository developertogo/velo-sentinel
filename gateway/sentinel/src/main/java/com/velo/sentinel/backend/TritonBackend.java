package com.velo.sentinel.backend;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.context.InferenceContext;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Service
public class TritonBackend implements InferenceBackend {

  private final TritonGrpcClient tritonClient;

  public TritonBackend(TritonGrpcClient tritonClient) {
    this.tritonClient = tritonClient;
  }

  @Override
  public float infer(float value) {
    String activeSession = InferenceContext.SESSION_ID.isBound()
        ? InferenceContext.SESSION_ID.get()
        : "legacy-session";
    return infer(value, activeSession);
  }

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TritonBackend.class);

  @Override
  public float infer(float value, String sessionId) {
    log.debug("TRITON-EXECUTION [Session: {}]: Forwarding request to legacy backend.", sessionId);

    // Call Triton via gRPC
    // Note: sessionId is propagated to this layer for future batching/routing support
    ModelInferResponse response = tritonClient.infer(value);

    // Extract raw bytes
    byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

    // Convert bytes → float (Little Endian)
    return ByteBuffer.wrap(rawBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .getFloat();
  }
}
