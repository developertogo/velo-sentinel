package com.velo.sentinel.backend;

public interface InferenceBackend {
  float infer(float value);
  float infer(float value, String sessionId);
}
