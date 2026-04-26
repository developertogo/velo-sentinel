package com.velo.sentinel.controller;

import com.velo.sentinel.backend.InferenceBackend;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
public class InferenceController {

  private final InferenceBackend backend;

  public InferenceController(InferenceBackend backend) {
    this.backend = backend;
  }

  @PostMapping("/infer")
  public Map<String, Object> infer(@RequestBody Map<String, Float> body) {
    float value = body.getOrDefault("value", 0.0f);

    float result = backend.infer(value);

    return Map.of(
        "model", "simple",
        "input_value", value,
        "prediction", result,
        "status", "SUCCESS");
  }
}
