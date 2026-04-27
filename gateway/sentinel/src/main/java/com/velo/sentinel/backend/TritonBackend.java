package com.velo.sentinel.backend;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.service.DynamoBridgeService;

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
    String activeSession = DynamoBridgeService.SESSION_ID.isBound()
        ? DynamoBridgeService.SESSION_ID.get()
        : "legacy-session";
    return infer(value, activeSession);
  }

  @Override
  public float infer(float value, String sessionId) {
    System.out.println("TritonBackend is used for session: " + sessionId);

    // Call Triton via gRPC
    ModelInferResponse response = tritonClient.infer(value);

    // Extract raw bytes
    byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

    // Convert bytes → float (Little Endian)
    return ByteBuffer.wrap(rawBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .getFloat();
  }
}
