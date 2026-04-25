package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class DynamoBridgeService implements InferenceBackend {

  private final TritonBackend tritonBackend;

  public DynamoBridgeService(TritonBackend tritonBackend) {
    this.tritonBackend = tritonBackend;
  }

  @Override
  public float infer(float value) {
    System.out.println("DynamoBridgeService is used"); // 👈 debug

    // For now: always route to Triton
    return tritonBackend.infer(value);
  }
}
