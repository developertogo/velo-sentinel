package com.velo.sentinel.controller;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.model.InferenceRequest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class InferenceController {

  private final InferenceBackend backend;

  public InferenceController(InferenceBackend backend) {
    this.backend = backend;
  }

  @PostMapping("/infer")
  public ResponseEntity<Map<String, Object>> infer(@RequestBody InferenceRequest request) {
    // 1. Capture the 'what' (value) and 'who' (sessionId) from the DTO
    // 2. Pass them to the bridge
    float prediction = backend.infer(request.value(), request.sessionId());

    return ResponseEntity.ok(Map.of(
        "status", "SUCCESS",
        "prediction", prediction,
        "sessionId", request.sessionId() != null ? request.sessionId() : "anonymous"));
  }
}
