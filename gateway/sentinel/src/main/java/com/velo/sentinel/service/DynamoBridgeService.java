package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.backend.MetalBackend;
import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
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
  private final MetalBackend metalBackend;
  private final SpeculativeOrchestrator speculativeOrchestrator;
  private final DynamoResilienceComponent resilienceComponent;
  private final AdaptiveBatcher adaptiveBatcher;
  private final Tracer tracer;
  private final RequestThrottler throttler;
  private final DriftMonitor driftMonitor;
  private final ChaosComponent chaosComponent;
  private final KVCacheRegistry kvCacheRegistry;
  private final SemanticCacheService semanticCache;

  @Value("${velo.sentinel.routing-mode:TRITON}")
  private RoutingMode routingMode;

  @Value("${velo.sentinel.latency-threshold-ms:200.0}")
  private double latencyThresholdMs;

  @Value("${velo.sentinel.canary-percentage:5}")
  private int canaryPercentage;

  @Value("${velo.sentinel.hedging.enabled:false}")
  private boolean hedgingEnabled;

  @Value("${velo.sentinel.hedging.delay-ms:50.0}")
  private float hedgingDelayMs;

  public enum RoutingMode {
    TRITON, DYNAMO, SHADOW, CANARY
  }

  public DynamoBridgeService(TritonBackend tritonBackend,
      DynamoBackend dynamoBackend,
      MetalBackend metalBackend,
      SpeculativeOrchestrator speculativeOrchestrator,
      MeterRegistry meterRegistry,
      DynamoResilienceComponent resilienceComponent,
      AdaptiveBatcher adaptiveBatcher,
      Tracer tracer,
      RequestThrottler throttler,
      DriftMonitor driftMonitor,
      ChaosComponent chaosComponent,
      KVCacheRegistry kvCacheRegistry,
      SemanticCacheService semanticCache) {
    this.tritonBackend = tritonBackend;
    this.dynamoBackend = dynamoBackend;
    this.metalBackend = metalBackend;
    this.speculativeOrchestrator = speculativeOrchestrator;
    this.meterRegistry = meterRegistry;
    this.resilienceComponent = resilienceComponent;
    this.adaptiveBatcher = adaptiveBatcher;
    this.tracer = tracer;
    this.throttler = throttler;
    this.driftMonitor = driftMonitor;
    this.chaosComponent = chaosComponent;
    this.kvCacheRegistry = kvCacheRegistry;
    this.semanticCache = semanticCache;
  }

  /**
   * Executes inference with a default session and model.
   * 
   * @param value The input value for prediction.
   * @return The predicted value from the chosen backend.
   */
  @Override
  public float infer(float value) {
    String session = determineSession();
    return infer(value, session, "simple");
  }

  @Override
  public float infer(float value, String sessionId) {
    return infer(value, sessionId, "simple");
  }

  /**
   * Executes inference for a specific session with a default model.
   * 
   * @param value     The input value for prediction.
   * @param sessionId The unique identifier for the user session.
   * @param modelName The name of the model to use.
   * @return The predicted value from the chosen backend.
   */
  @Override
  public float infer(float value, String sessionId, String modelName) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    try {
      return InferenceContext.runInContext(safeSession, () -> orchestrateInference(value, safeSession, modelName,
          PriorityTier.INTERACTIVE, 0, ModelPrecision.FP16, false));
    } catch (Exception e) {
      throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  @Override
  public float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity) {
    return sentinelExecute(value, sessionId, modelName, priority, complexity, ModelPrecision.FP16, false);
  }

  @Override
  public float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity,
      ModelPrecision precision) {
    return sentinelExecute(value, sessionId, modelName, priority, complexity, precision, false);
  }

  @Override
  public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity,
      ModelPrecision precision, boolean useAgenticOptimization) {
    String safeSession = (sessionId != null) ? sessionId : "anonymous";
    try {
      return InferenceContext.runInContext(safeSession, () -> orchestrateInference(value, safeSession, modelName,
          priority, complexity, precision, useAgenticOptimization));
    } catch (Exception e) {
      throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  /**
   * The core inference orchestration logic.
   * Handles tracing, metrics, and routing based on the current RoutingMode.
   * 
   * @param value     The input value.
   * @param sessionId The session ID.
   * @param modelName The model name.
   * @param priority  The priority tier (SLA aware).
   * @return The final prediction.
   */
  private float orchestrateInference(float value, String sessionId, String modelName, PriorityTier priority,
      int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
    if (sessionId.startsWith("private-")) {
      log.info("HYBRID-ROUTING [Session: {}]: Request is local-eligible. Offloading to M3 GPU (Metal).", sessionId);
      return metalBackend.infer(value, sessionId, modelName);
    }

    // Semantic Cache Check
    String promptKey = (modelName != null ? modelName : "unknown") + ":" + value;
    Float cachedResult = semanticCache != null ? semanticCache.checkCache(promptKey) : null;
    if (cachedResult != null) {
      log.info("CACHE-HIT [Model: {}]: Returning result from semantic cache.", modelName);
      return cachedResult;
    }

    RoutingMode effectiveMode = routingMode;

    // HYBRID-ROUTING: Offload small or "privacy-sensitive" prompts to local M3
    // (Metal)
    if (sessionId.startsWith("private-")) {
      log.info("HYBRID-ROUTING [Session: {}]: Request is local-eligible. Offloading to M3 GPU (Metal).", sessionId);
      return metalBackend.infer(value, sessionId, modelName);
    }

    // SPECULATIVE-DECODING: Orchestrate local M3 Drafter + Cloud Target
    if (useAgenticOptimization && effectiveMode == RoutingMode.DYNAMO) {
      log.info("SPECULATIVE-DECODING [Session: {}]: Speculative mode active. Orchestrating Drafter/Target.", sessionId);
      return speculativeOrchestrator.executeSpeculative(value, sessionId, modelName, dynamoBackend);
    }

    // CONTEXTUAL-ROUTING: If complexity is low, force TRITON to save cost
    if (complexity < 50 && complexity > 0) {
      log.info(
          "CONTEXTUAL-ROUTING [Session: {}]: Complexity {} is below threshold (50). Routing to Efficiency Node (TRITON).",
          sessionId, complexity);
      effectiveMode = RoutingMode.TRITON;
    }

    // QUANTIZATION-AWARE: Log precision intent (In real scenario, we'd pick a
    // specific model variation)
    if (precision != com.velo.sentinel.model.ModelPrecision.FP16) {
      log.info("QUANTIZATION-AWARE [Session: {}]: Routing to {} optimized backend.", sessionId, precision);
    }

    // SAFETY-SWITCH: If accuracy drift is detected, veto Dynamo and force Triton
    if (driftMonitor.isVetoActive()) {
      if (effectiveMode != RoutingMode.TRITON) {
        log.error(
            "SAFETY-VETO: Accuracy drift exceeds safety limits. Overriding {} mode and forcing FAILBACK to TRITON ground truth.",
            effectiveMode);
      }
      effectiveMode = RoutingMode.TRITON;
    }

    final RoutingMode finalMode = effectiveMode;
    return throttler.throttle(sessionId, () -> {
      Span span = tracer.spanBuilder("VeloInference")
          .setAttribute("inference.model", modelName != null ? modelName : "unknown")
          .setAttribute("inference.mode", finalMode.name())
          .setAttribute("inference.session", sessionId)
          .setAttribute("inference.priority", priority != null ? priority.name() : "NONE")
          .startSpan();

      try (Scope scope = span.makeCurrent()) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
          // Orchestrate with Hedging if enabled
          if (hedgingEnabled && finalMode != RoutingMode.TRITON) {
            return executeHedgedInference(value, sessionId, modelName, finalMode, priority, complexity, precision,
                useAgenticOptimization);
          }

          float result = switch (finalMode) {
            case DYNAMO -> routeToDynamo(value, sessionId, modelName, priority);
            case SHADOW -> routeShadow(value, sessionId, modelName);
            case TRITON -> routeToTriton(value, sessionId, modelName);
            case CANARY -> {
              // Deterministic session-based canary split
              int bucket = Math.abs(sessionId.hashCode()) % 100;
              if (bucket < canaryPercentage) {
                log.debug("CANARY-ROUTE: Routing session {} to DYNAMO (Bucket: {})", sessionId, bucket);
                yield routeToDynamo(value, sessionId, modelName, priority);
              } else {
                log.debug("CANARY-ROUTE: Routing session {} to TRITON (Bucket: {})", sessionId, bucket);
                yield routeToTriton(value, sessionId, modelName);
              }
            }
          };
          sample.stop(meterRegistry.timer("velo.sentinel.inference", "mode", finalMode.name(), "model", modelName));

          // Cache the result
          if (semanticCache != null) {
            semanticCache.updateCache(modelName + ":" + value, result);
          }

          return result;
        } catch (Exception e) {
          meterRegistry.counter("velo.sentinel.errors", "mode", finalMode.name(), "model", modelName).increment();
          span.recordException(e);
          span.setStatus(StatusCode.ERROR, e.getMessage());
          throw e;
        }
      } finally {
        span.end();
      }
    });
  }

  private float orchestrateInference(float value, String sessionId, String modelName) {
    return orchestrateInference(value, sessionId, modelName, PriorityTier.INTERACTIVE, 0, ModelPrecision.FP16, false);
  }

  private float orchestrateInference(float value, String sessionId, String modelName, PriorityTier priority,
      int complexity) {
    return orchestrateInference(value, sessionId, modelName, priority, complexity, ModelPrecision.FP16, false);
  }

  private float orchestrateInference(float value, String sessionId, String modelName, PriorityTier priority,
      int complexity, ModelPrecision precision) {
    return orchestrateInference(value, sessionId, modelName, priority, complexity, precision, false);
  }

  /**
   * Hedged Inference: Shaves the tail of the latency distribution.
   * Starts primary, and if it exceeds delayMs, starts a second request.
   */
  private float executeHedgedInference(float value, String sessionId, String modelName, RoutingMode mode,
      PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
    log.debug("HEDGING-INIT [Session: {}]: Hedging enabled. Delay: {}ms", sessionId, hedgingDelayMs);

    CompletableFuture<Float> primary = CompletableFuture.supplyAsync(() -> {
      return switch (mode) {
        case DYNAMO -> routeToDynamo(value, sessionId, modelName, priority);
        case SHADOW -> routeShadow(value, sessionId, modelName);
        case CANARY -> {
          int bucket = Math.abs(sessionId.hashCode()) % 100;
          if (bucket < canaryPercentage) {
            yield routeToDynamo(value, sessionId, modelName, priority);
          } else {
            yield routeToTriton(value, sessionId, modelName);
          }
        }
        default -> routeToTriton(value, sessionId, modelName);
      };
    });

    try {
      // Wait for primary with the hedging delay
      return primary.get((long) hedgingDelayMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      // Primary took too long (or failed). Spawn a hedge!
      log.warn("HEDGING-TRIGGER [Session: {}]: Primary exceeded {}ms. Spawning hedged request to Triton.",
          sessionId, hedgingDelayMs);

      CompletableFuture<Float> hedge = CompletableFuture.supplyAsync(() -> routeToTriton(value, sessionId, modelName));

      // Return the fastest between primary and hedge
      return (float) CompletableFuture.anyOf(primary, hedge).join();
    }
  }

  private String determineSession() {
    return InferenceContext.SESSION_ID.isBound() ? InferenceContext.SESSION_ID.get() : "legacy-path";
  }

  private float routeToTriton(float value, String sessionId, String modelName) {
    log.info("SENTINEL-MODE [TRITON]: Executing standard legacy path for model {}.", modelName);
    return tritonBackend.infer(value, sessionId, modelName);
  }

  private float routeToDynamo(float value, String sessionId, String modelName,
      com.velo.sentinel.model.PriorityTier priority) {
    try {
      boolean isPrefill = !kvCacheRegistry.isSessionWarm(sessionId);
      log.debug("DISAGGREGATED-ROUTING [Session: {}]: Type={}", sessionId, isPrefill ? "PREFILL" : "DECODE");

      return adaptiveBatcher.submit(value, sessionId, modelName, priority, isPrefill, items -> {
        return items.stream()
            .map(item -> {
              try {
                return InferenceContext.runInContext(item.sessionId(), () -> {
                  chaosComponent.maybeInjectChaos(item.modelName());
                  float res = resilienceComponent.protectedDynamoCall(item.value(), item.sessionId(), item.modelName());
                  // Mark session as warm after successful prefill
                  if (item.isPrefill()) {
                    kvCacheRegistry.markSessionActive(item.sessionId());
                  }
                  return res;
                });
              } catch (Exception e) {
                throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
              }
            })
            .toList();
      }).get((long) latencyThresholdMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.warn(
          "SLO-VETO [Model: {}]: Dynamo path exceeded {}ms or failed. Falling back to Triton to protect P99. Reason: {}",
          modelName, latencyThresholdMs, e.getMessage());
      return routeToTriton(value, sessionId, modelName);
    }
  }

  private float routeShadow(float value, String sessionId, String modelName) {
    log.info("SENTINEL-MODE [SHADOW]: Performing side-by-side validation for model {}...", modelName);

    // Get Triton result with a tight, bounded scope.
    // We do NOT block on Dynamo here — that would add latency to the user-facing
    // response.
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
        log.error("SHADOW-CRITICAL: Triton (Ground Truth) failed (State: {}). Attempting best-effort Dynamo fallback.",
            tritonTask.state());
        // Triton is the ground truth — if it's down, fall back to Dynamo as best-effort
        // rather than retrying a known-broken backend.
        return resilienceComponent.protectedDynamoCall(value, sessionId, modelName);
      }

      tritonResult = tritonTask.get();
    } catch (Exception e) {
      return tritonBackend.infer(value, sessionId, modelName);
    }

    // Kick off Dynamo comparison in the background — completely decoupled
    // from the
    // user response. This guarantees Shadow mode NEVER adds latency to the critical
    // path.
    final float capturedResult = tritonResult;
    CompletableFuture.runAsync(() -> {
      try {
        InferenceContext.runInContext(sessionId, () -> {
          float dynamoResult = resilienceComponent.protectedDynamoCall(value, sessionId, modelName);

          // RECORD-OBSERVATION: Feed the drift monitor
          driftMonitor.recordObservation(capturedResult, dynamoResult);

          float drift = Math.abs(capturedResult - dynamoResult);
          if (drift > 0.5) {
            log.info("SHADOW-COMPARISON [Model: {}]: Triton={}, Dynamo={}, Drift={}", modelName, capturedResult,
                dynamoResult, drift);
          }
          DistributionSummary.builder("velo.sentinel.shadow.drift").tag("model", modelName).register(meterRegistry)
              .record(drift);
          meterRegistry.counter("velo.sentinel.shadow.comparisons", "model", modelName).increment();
          return null;
        });
      } catch (Exception e) {
        log.warn("SHADOW-VETO [Model: {}]: Dynamo comparison failed asynchronously: {}", modelName, e.getMessage());
        meterRegistry.counter("velo.sentinel.shadow.timeout", "model", modelName).increment();
      }
    });

    return tritonResult;
  }
}
