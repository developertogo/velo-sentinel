package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.velo.sentinel.context.InferenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;
import java.time.Duration;
import java.time.Instant;

/**
 * DynamoBridgeService: The L5 Migration Controller.
 * 
 * This service acts as the central orchestrator for the Velo-Sentinel gateway.
 * It manages the transition from legacy Triton backends to next-gen disaggregated 
 * Dynamo architectures using a three-tier routing strategy (TRITON, DYNAMO, SHADOW).
 * 
 * Key Responsibilities:
 * 1. Orchestrates concurrent execution in SHADOW mode using Java 25 Structured Concurrency.
 * 2. Implements Fail-Open resilience via Resilience4j Circuit Breakers.
 * 3. Tracks quantitative model drift using Micrometer DistributionSummaries.
 * 4. Manages session context propagation using ScopedValues.
 */
@Service
@Primary
public class DynamoBridgeService implements InferenceBackend {
  private final MeterRegistry meterRegistry;

  private static final Logger log = LoggerFactory.getLogger(DynamoBridgeService.class);
  private final TritonBackend tritonBackend;
  private final DynamoBackend dynamoBackend;

  /**
   * Defines the active routing strategy.
   * TRITON: Legacy path only.
   * DYNAMO: Next-gen path with legacy fallback.
   * SHADOW: Concurrent execution with side-by-side drift validation.
   */
  @Value("${velo.sentinel.routing-mode:TRITON}")
  private RoutingMode routingMode;

  /**
   * The Maximum Latency Threshold for shadow validation (SLO).
   * If Dynamo exceeds this threshold, the shadow result is vetoed to protect 
   * production response times.
   */
  @Value("${velo.sentinel.latency-threshold-ms:200.0}")
  private double latencyThresholdMs;

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

  /**
   * Standard inference entry point.
   * Binds a default or recovered session context before executing the routing logic.
   */
  @Override
  public float infer(float value) {
    String session = determineSession();
    return ScopedValue.where(InferenceContext.SESSION_ID, session)
        .call(() -> executeInference(value));
  }

  /**
   * Session-aware inference entry point.
   * Explicitly binds the provided sessionId for the duration of the inference call.
   * 
   * @param value The input feature value for inference.
   * @param sessionId The unique identifier for the user session (used for KV-Cache routing).
   * @return The resulting prediction from the active backend.
   */
  @Override
  public float infer(float value, String sessionId) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    return ScopedValue.where(InferenceContext.SESSION_ID, safeSession)
        .call(() -> executeInference(value));
  }

  /**
   * Internal execution engine. Tracks latency and routes to the appropriate 
   * backend based on the configured RoutingMode.
   */
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
    return InferenceContext.SESSION_ID.isBound() ? InferenceContext.SESSION_ID.get() : "legacy-path";
  }

  /**
   * Resilience4j Fallback Handler.
   * Ensures 100% availability by failing open to the legacy Triton backend 
   * if the Dynamo path encounters errors or trips the circuit breaker.
   */
  public float failOpenToTriton(float value, Throwable t) {
    log.error("DYNAMO-FAILURE: Circuit is OPEN or Call Failed. Reason: {}. Failing open to Triton.",
        t.getMessage());
    return tritonBackend.infer(value);
  }

  private float routeToTriton(float value) {
    log.info("SENTINEL-MODE [TRITON]: Executing standard legacy path.");
    return tritonBackend.infer(value);
  }

  private float routeToDynamo(float value) {
    log.info("SENTINEL-MODE [DYNAMO]: Executing next-gen Dynamo path.");
    return protectedDynamoCall(value);
  }

  /**
   * Isolated Dynamo call protected by a dedicated Circuit Breaker.
   * Separates Dynamo failures from Triton's health metrics.
   */
  @CircuitBreaker(name = "dynamoBackend", fallbackMethod = "failOpenToTriton")
  public float protectedDynamoCall(float value) {
    return dynamoBackend.infer(value);
  }

  /**
   * SHADOW MODE: Orchestrates concurrent execution of both backends.
   * 
   * Uses Java 25 StructuredTaskScope to fork both requests. 
   * The 'Ground Truth' (Triton) is returned to the caller, while the 
   * Dynamo result is validated in the background.
   * 
   * Drift between models is recorded as a Micrometer DistributionSummary 
   * for real-time observability.
   */
  private float routeShadow(float value) {
    log.info("SENTINEL-MODE [SHADOW]: Performing side-by-side validation...");

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll(),
        cfg -> cfg.withTimeout(java.time.Duration.ofMillis((long) latencyThresholdMs)))) {

      var tritonTask = scope.fork(() -> tritonBackend.infer(value));
      var dynamoTask = scope.fork(() -> protectedDynamoCall(value));

      try {
        scope.join(); 
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("SHADOW-INTERRUPTED: Execution interrupted.");
      }

      float tritonResult;
      if (tritonTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
        tritonResult = tritonTask.get();
      } else {
        log.error("SHADOW-CRITICAL: Triton (Ground Truth) failed or timed out. Falling back to direct call.");
        return tritonBackend.infer(value);
      }

      // Quantatitive Drift Detection
      if (dynamoTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
        float dynamoResult = dynamoTask.get();
        float drift = Math.abs(tritonResult - dynamoResult);
        
        log.info("SHADOW-COMPARISON: Triton={}, Dynamo={}, Drift={}", tritonResult, dynamoResult, drift);
        
        DistributionSummary.builder("velo.sentinel.shadow.drift")
            .description("Absolute difference between Triton and Dynamo results")
            .tag("session", InferenceContext.SESSION_ID.isBound() ? "active" : "anonymous")
            .register(meterRegistry)
            .record(drift);

        meterRegistry.counter("velo.sentinel.shadow.comparisons").increment();
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
