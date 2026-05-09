package com.velo.sentinel.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * TritonClient: Legacy HTTP-based interface for NVIDIA Triton.
 * Used for simple REST-based inference calls.
 */
@Service
public class TritonClient {

  private final RestClient restClient;

  /**
   * Initializes the REST client with the Triton base URL.
   */
  public TritonClient() {
    this.restClient = RestClient.builder()
        .baseUrl("http://localhost:8000") // Triton HTTP endpoint
        .build();
  }

  /**
   * Executes a synchronous REST inference call.
   * 
   * @param value The input float value.
   * @return The raw JSON response from Triton.
   */
  public String infer(float value) {
    String body = """
        {
          "inputs": [{
            "name": "INPUT",
            "shape": [1],
            "datatype": "FP32",
            "data": [%f]
          }]
        }
        """.formatted(value);

    return restClient.post()
        .uri("/v2/models/simple/infer") // Triton expects model name here
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }
}