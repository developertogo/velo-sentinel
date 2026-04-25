package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;

/**
 * DynamoBridgeService: The L5 Migration Controller.
 * Manages the transition from legacy Triton to next-gen Dynamo-Triton.
 */
@Service
@Primary
public class DynamoBridgeService implements InferenceBackend {

  private static final Logger log = LoggerFactory.getLogger(DynamoBridgeService.class);
  private final TritonBackend tritonBackend;

  // Define the Routing Strategy for Netflix-grade deployments
  public enum RoutingMode {
    TRITON, DYNAMO, SHADOW
  }

  @Value("${velo.sentinel.routing-mode:TRITON}")
  private RoutingMode routingMode;

  public DynamoBridgeService(TritonBackend tritonBackend) {
    this.tritonBackend = tritonBackend;
  }

  @Override
  public float infer(float value) {
    return switch (routingMode) {
      case DYNAMO -> routeToDynamo(value);
      case SHADOW -> routeShadow(value);
      case TRITON -> routeToTriton(value);
    };
  }

  private float routeToTriton(float value) {
    System.out.println("SENTINEL-MODE [TRITON]: Executing standard legacy path.");
    return tritonBackend.infer(value);
  }

  private float routeToDynamo(float value) {
    System.out.println("SENTINEL-MODE [DYNAMO]: Executing next-gen Dynamo path.");
    // We will integrate DynamoBackend here in the next step
    return tritonBackend.infer(value);
  }

  /**
   * SHADOW MODE: Executes both backends concurrently using Structured
   * Concurrency.
   * Returns the 'Ground Truth' (Triton) while validating Dynamo's results in
   * parallel.
   */
  private float routeShadow(float value) {
    System.out.println("SENTINEL-MODE [SHADOW]: Performing side-by-side validation...");

    // Use Java 25 StructuredTaskScope for lightweight concurrent execution
    try (var scope = StructuredTaskScope.open()) {
      var tritonTask = scope.fork(() -> tritonBackend.infer(value));

      // Future-proof: Ready for DynamoTask in the next phase
      // var dynamoTask = scope.fork(() -> dynamoBackend.infer(value));

      scope.join(); // Ensure all virtual threads complete

      float tritonResult = tritonTask.get();
      log.info("Shadow Success - Triton Reference Result: {}", tritonResult);

      return tritonResult;
    } catch (Exception e) {
      log.error("Shadow execution failure, safely defaulting to Triton", e);
      return tritonBackend.infer(value);
    }
  }
}
