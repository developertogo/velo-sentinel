package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

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
  private final MeterRegistry meterRegistry;

  private static final Logger log = LoggerFactory.getLogger(DynamoBridgeService.class);
  private final TritonBackend tritonBackend;
  private final DynamoBackend dynamoBackend;

  @Value("${velo.sentinel.routing-mode:TRITON}")
  private RoutingMode routingMode;

  // SLO Threshold: 200ms
  private static final double LATENCY_THRESHOLD_MS = 200.0;

  // Define the Routing Strategy for Netflix-grade deployments
  public enum RoutingMode {
    TRITON, DYNAMO, SHADOW
  }

  public DynamoBridgeService(TritonBackend tritonBackend,
      DynamoBackend dynamoBackend,
      MeterRegistry meterRegistry) {
    this.tritonBackend = tritonBackend;
    this.dynamoBackend = dynamoBackend;
    this.meterRegistry = meterRegistry;
  }

  // The "Invisible Pipe" for the Session ID
  public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();

  /**
   * Satisfies the InferenceBackend Interface.
   * Used by legacy callers who don't know about sessions.
   */
  @Override
  @CircuitBreaker(name = "dynamoBackend", fallbackMethod = "failOpenToTriton")
  public float infer(float value) {
    String session = determineSession();
    return ScopedValue.where(SESSION_ID, session)
        .call(() -> executeInference(value));
  }

  /**
   * Overloaded method for the Controller.
   * Explicitly binds the session provided in the InferenceRequest DTO.
   */
  @Override
  @CircuitBreaker(name = "dynamoBackend", fallbackMethod = "failOpenToTriton")
  public float infer(float value, String sessionId) {
    // Ensure we never pass a null into the ScopedValue/Backends
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    return ScopedValue.where(SESSION_ID, safeSession)
        .call(() -> executeInference(value));
  }

  private float executeInference(float value) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      float result = switch (routingMode) {
        case DYNAMO -> routeToDynamo(value);
        case SHADOW -> routeShadow(value);
        case TRITON -> routeToTriton(value);
      };
      sample.stop(meterRegistry.timer("velo.sentinel.inference", "mode", routingMode.name()));
      return result;
    } catch (Exception e) {
      meterRegistry.counter("velo.sentinel.errors", "mode", routingMode.name()).increment();
      throw e;
    }
  }

  private String determineSession() {
    // If the Controller already bound a session, use it.
    // Otherwise, default to "legacy-path".
    return SESSION_ID.isBound() ? SESSION_ID.get() : "legacy-path";
  }

  /**
   * The Fail-Open Fallback.
   * This is triggered if:
   * 1. DynamoBackend throws an Exception
   * 2. The Circuit is OPEN (Dynamo is already known to be down)
   */
  public float failOpenToTriton(float value, Throwable t) {
    log.error("DYNAMO-FAILURE [Legacy]: Circuit is OPEN or Call Failed. Reason: {}. Failing open to Triton.",
        t.getMessage());
    return tritonBackend.infer(value);
  }

  /**
   * Fallback for the overloaded Controller method: infer(float, String)
   * This matches the signature the CircuitBreaker is looking for in your logs.
   */
  public float failOpenToTriton(float value, String sessionId, Throwable t) {
    log.error("DYNAMO-FAILURE [Session: {}]: Circuit is OPEN or Call Failed. Reason: {}. Failing open to Triton.",
        sessionId, t.getMessage());
    return tritonBackend.infer(value);
  }

  private float routeToTriton(float value) {
    System.out.println("SENTINEL-MODE [TRITON]: Executing standard legacy path.");
    return tritonBackend.infer(value);
  }

  private float routeToDynamo(float value) {
    System.out.println("SENTINEL-MODE [DYNAMO]: Executing next-gen Dynamo path.");
    return dynamoBackend.infer(value);
  }

  /**
   * SHADOW MODE: Executes both backends concurrently using Structured
   * Concurrency.
   * Returns the 'Ground Truth' (Triton) while validating Dynamo's results in
   * parallel.
   */
  private float routeShadow(float value) {
    System.out.println("SENTINEL-MODE [SHADOW]: Performing side-by-side validation...");

    // Use Java 25 StructuredTaskScope with a configuration-based timeout
    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll(),
        cfg -> cfg.withTimeout(java.time.Duration.ofMillis((long) LATENCY_THRESHOLD_MS)))) {

      var tritonTask = scope.fork(() -> tritonBackend.infer(value));
      var dynamoTask = scope.fork(() -> dynamoBackend.infer(value));

      try {
        scope.join(); // This will now obey the 'allUntil' deadline
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("SHADOW-INTERRUPTED: Execution interrupted.");
      }

      float tritonResult;
      if (tritonTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
        tritonResult = tritonTask.get();
        log.info("Shadow Success - Triton Reference Result: {}", tritonResult);
      } else {
        log.error("SHADOW-CRITICAL: Triton (Ground Truth) failed or timed out. Falling back to direct call.");
        return tritonBackend.infer(value); // One last sync attempt
      }

      // Only attempt to get Dynamo result if it actually finished successfully within
      // the deadline
      if (dynamoTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
        float dynamoResult = dynamoTask.get();
        float drift = Math.abs(tritonResult - dynamoResult);
        log.info("SHADOW-COMPARISON: Triton={}, Dynamo={}, Drift={}", tritonResult, dynamoResult, drift);
      } else {
        log.warn("SHADOW-VETO: Dynamo result unavailable or slow (State: {}). Skipping comparison.",
            dynamoTask.state());
        meterRegistry.counter("velo.sentinel.shadow.timeout").increment();
      }

      return tritonResult;
    } catch (Exception e) {
      log.error("Shadow execution failure, safely defaulting to Triton", e);
      return tritonBackend.infer(value);
    }
  }
}
