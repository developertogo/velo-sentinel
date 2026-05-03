package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
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

/**
 * DynamoBridgeService: The L5 Migration Controller.
 * 
 * Orchestrates routing between legacy Triton and next-gen Dynamo architectures.
 * Uses DynamoResilienceComponent for fault-tolerant execution.
 */
@Service
@Primary
public class DynamoBridgeService implements InferenceBackend {
  private final MeterRegistry meterRegistry;

  private static final Logger log = LoggerFactory.getLogger(DynamoBridgeService.class);
  private final TritonBackend tritonBackend;
  private final DynamoBackend dynamoBackend;
  private final DynamoResilienceComponent resilienceComponent;

  @Value("${velo.sentinel.routing-mode:TRITON}")
  private RoutingMode routingMode;

  @Value("${velo.sentinel.latency-threshold-ms:200.0}")
  private double latencyThresholdMs;

  public enum RoutingMode {
    TRITON, DYNAMO, SHADOW
  }

  public DynamoBridgeService(TritonBackend tritonBackend,
      DynamoBackend dynamoBackend,
      MeterRegistry meterRegistry,
      DynamoResilienceComponent resilienceComponent) {
    this.tritonBackend = tritonBackend;
    this.dynamoBackend = dynamoBackend;
    this.meterRegistry = meterRegistry;
    this.resilienceComponent = resilienceComponent;
  }

  @Override
  public float infer(float value) {
    String session = determineSession();
    return ScopedValue.where(InferenceContext.SESSION_ID, session)
        .call(() -> executeInference(value));
  }

  @Override
  public float infer(float value, String sessionId) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    return ScopedValue.where(InferenceContext.SESSION_ID, safeSession)
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
    return InferenceContext.SESSION_ID.isBound() ? InferenceContext.SESSION_ID.get() : "legacy-path";
  }

  private float routeToTriton(float value) {
    log.info("SENTINEL-MODE [TRITON]: Executing standard legacy path.");
    return tritonBackend.infer(value);
  }

  private float routeToDynamo(float value) {
    log.info("SENTINEL-MODE [DYNAMO]: Executing next-gen Dynamo path.");
    return resilienceComponent.protectedDynamoCall(value);
  }

  private float routeShadow(float value) {
    log.info("SENTINEL-MODE [SHADOW]: Performing side-by-side validation...");

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll(),
        cfg -> cfg.withTimeout(java.time.Duration.ofMillis((long) latencyThresholdMs)))) {

      var tritonTask = scope.fork(() -> tritonBackend.infer(value));
      var dynamoTask = scope.fork(() -> resilienceComponent.protectedDynamoCall(value));

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
        log.error("SHADOW-CRITICAL: Triton failed (State: {}). Falling back.", tritonTask.state());
        return tritonBackend.infer(value);
      }

      if (dynamoTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
        float dynamoResult = dynamoTask.get();
        float drift = Math.abs(tritonResult - dynamoResult);
        log.info("SHADOW-COMPARISON: Triton={}, Dynamo={}, Drift={}", tritonResult, dynamoResult, drift);
        DistributionSummary.builder("velo.sentinel.shadow.drift").register(meterRegistry).record(drift);
        meterRegistry.counter("velo.sentinel.shadow.comparisons").increment();
      } else {
        log.warn("SHADOW-VETO: Dynamo slow (State: {}).", dynamoTask.state());
        meterRegistry.counter("velo.sentinel.shadow.timeout").increment();
      }

      return tritonResult;
    } catch (Exception e) {
      return tritonBackend.infer(value);
    }
  }
}
