package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.velo.sentinel.context.InferenceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

/**
 * DynamoBridgeService: The L5 Migration Controller.
 * 
 * Orchestrates routing between legacy Triton and next-gen Dynamo architectures.
 * Uses DynamoResilienceComponent for fault-tolerant execution.
 * Supports Multi-model Dynamic Routing and OpenTelemetry Tracing.
 */
@Service
@Primary
public class DynamoBridgeService implements InferenceBackend {
  private final MeterRegistry meterRegistry;

  private static final Logger log = LoggerFactory.getLogger(DynamoBridgeService.class);
  private final TritonBackend tritonBackend;
  private final DynamoBackend dynamoBackend;
  private final DynamoResilienceComponent resilienceComponent;
  private final AdaptiveBatcher adaptiveBatcher;
  private final Tracer tracer;

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
      DynamoResilienceComponent resilienceComponent,
      AdaptiveBatcher adaptiveBatcher,
      Tracer tracer) {
    this.tritonBackend = tritonBackend;
    this.dynamoBackend = dynamoBackend;
    this.meterRegistry = meterRegistry;
    this.resilienceComponent = resilienceComponent;
    this.adaptiveBatcher = adaptiveBatcher;
    this.tracer = tracer;
  }

  @Override
  public float infer(float value) {
    String session = determineSession();
    return infer(value, session, "simple");
  }

  @Override
  public float infer(float value, String sessionId) {
    return infer(value, sessionId, "simple");
  }

  @Override
  public float infer(float value, String sessionId, String modelName) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    return ScopedValue.where(InferenceContext.SESSION_ID, safeSession)
        .call(() -> executeInference(value, safeSession, modelName));
  }

  @Override
  public float infer(float value, String sessionId, String modelName, com.velo.sentinel.model.PriorityTier priority) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    return ScopedValue.where(InferenceContext.SESSION_ID, safeSession)
        .call(() -> executeInference(value, safeSession, modelName, priority));
  }

  private float executeInference(float value, String sessionId, String modelName, com.velo.sentinel.model.PriorityTier priority) {
    Span span = tracer.spanBuilder("VeloInference")
        .setAttribute("inference.model", modelName)
        .setAttribute("inference.mode", routingMode.name())
        .setAttribute("inference.session", sessionId)
        .setAttribute("inference.priority", priority != null ? priority.name() : "NONE")
        .startSpan();

    try (Scope scope = span.makeCurrent()) {
      Timer.Sample sample = Timer.start(meterRegistry);
      try {
        float result = switch (routingMode) {
          case DYNAMO -> routeToDynamo(value, sessionId, modelName, priority);
          case SHADOW -> routeShadow(value, sessionId, modelName);
          case TRITON -> routeToTriton(value, sessionId, modelName);
        };
        sample.stop(meterRegistry.timer("velo.sentinel.inference", "mode", routingMode.name(), "model", modelName));
        return result;
      } catch (Exception e) {
        meterRegistry.counter("velo.sentinel.errors", "mode", routingMode.name(), "model", modelName).increment();
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
      }
    } finally {
      span.end();
    }
  }

  private float executeInference(float value, String sessionId, String modelName) {
      return executeInference(value, sessionId, modelName, com.velo.sentinel.model.PriorityTier.INTERACTIVE);
  }

  private String determineSession() {
    return InferenceContext.SESSION_ID.isBound() ? InferenceContext.SESSION_ID.get() : "legacy-path";
  }

  private float routeToTriton(float value, String sessionId, String modelName) {
    log.info("SENTINEL-MODE [TRITON]: Executing standard legacy path for model {}.", modelName);
    return tritonBackend.infer(value, sessionId, modelName);
  }

  private float routeToDynamo(float value, String sessionId, String modelName, com.velo.sentinel.model.PriorityTier priority) {
    try {
      return adaptiveBatcher.submit(value, sessionId, modelName, priority, items -> {
        // This runs in the batcher's execution context
        // For now, we process them one by one but in the same 'batch' execution block
        // In a real gRPC backend, we would call a 'BatchedInfer' method
        return items.stream()
            .map(item -> resilienceComponent.protectedDynamoCall(item.value(), item.sessionId(), item.modelName()))
            .toList();
      }).get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("BATCHING-FAILURE: Falling back to individual call. Reason: {}", e.getMessage());
      return resilienceComponent.protectedDynamoCall(value, sessionId, modelName);
    }
  }

  private float routeShadow(float value, String sessionId, String modelName) {
    log.info("SENTINEL-MODE [SHADOW]: Performing side-by-side validation for model {}...", modelName);

    // Phase 1: Get Triton result with a tight, bounded scope.
    // We do NOT block on Dynamo here — that would add latency to the user-facing response.
    final float tritonResult;
    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll(),
        cfg -> cfg.withTimeout(java.time.Duration.ofMillis((long) latencyThresholdMs)))) {

      var tritonTask = scope.fork(() -> tritonBackend.infer(value, sessionId, modelName));

      try {
        scope.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("SHADOW-INTERRUPTED: Execution interrupted for model {}.", modelName);
      }

      if (tritonTask.state() != StructuredTaskScope.Subtask.State.SUCCESS) {
        log.error("SHADOW-CRITICAL: Triton (Ground Truth) failed (State: {}). Attempting best-effort Dynamo fallback.", tritonTask.state());
        // Triton is the ground truth — if it's down, fall back to Dynamo as best-effort
        // rather than retrying a known-broken backend.
        return resilienceComponent.protectedDynamoCall(value, sessionId, modelName);
      }

      tritonResult = tritonTask.get();
    } catch (Exception e) {
      return tritonBackend.infer(value, sessionId, modelName);
    }

    // Phase 2: Kick off Dynamo comparison in the background — completely decoupled from the
    // user response. This guarantees Shadow mode NEVER adds latency to the critical path.
    final float capturedResult = tritonResult;
    CompletableFuture.runAsync(() -> {
      try {
        float dynamoResult = resilienceComponent.protectedDynamoCall(value, sessionId, modelName);
        float drift = Math.abs(capturedResult - dynamoResult);
        log.info("SHADOW-COMPARISON [Model: {}]: Triton={}, Dynamo={}, Drift={}", modelName, capturedResult, dynamoResult, drift);
        DistributionSummary.builder("velo.sentinel.shadow.drift").tag("model", modelName).register(meterRegistry).record(drift);
        meterRegistry.counter("velo.sentinel.shadow.comparisons", "model", modelName).increment();
      } catch (Exception e) {
        log.warn("SHADOW-VETO [Model: {}]: Dynamo comparison failed asynchronously: {}", modelName, e.getMessage());
        meterRegistry.counter("velo.sentinel.shadow.timeout", "model", modelName).increment();
      }
    });

    return tritonResult;
  }
}

