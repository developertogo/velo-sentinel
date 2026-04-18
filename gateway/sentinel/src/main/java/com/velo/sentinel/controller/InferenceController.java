package com.velo.sentinel.controller;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
// @RequestMapping("/infer")
public class InferenceController {

  private final TritonGrpcClient tritonClient;

  public InferenceController(TritonGrpcClient tritonClient) {
    this.tritonClient = tritonClient;
  }

  @PostMapping("/infer")
  public Map<String, Object> infer(@RequestBody Map<String, Float> body) {
    float value = body.getOrDefault("value", 0.0f);

    ModelInferResponse response = tritonClient.infer(value);

    // Get the raw bytes from the first output
    byte[] rawBytes = response.getRawOutputContents(0).toByteArray();

    // Wrap in a ByteBuffer to convert bytes -> float
    // Triton uses Little Endian by default
    float result = ByteBuffer.wrap(rawBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .getFloat();

    return Map.of(
        "model", response.getModelName(),
        "input_value", value,
        "prediction", result,
        "status", "SUCCESS");
  }

  // @PostMapping
  // public String infer(@RequestBody FloatRequest request) {
  // ModelInferResponse response = tritonClient.infer(request.value());
  // return response.toString();
  // }

  // public record FloatRequest(float value) {}
}