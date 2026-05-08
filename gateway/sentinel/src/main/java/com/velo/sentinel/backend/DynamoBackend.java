package com.velo.sentinel.backend;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.service.KVCacheRegistry;
import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.velo.sentinel.context.InferenceContext;

/**
 * DynamoBackend: The Next-Gen Disaggregated Inference Engine.
 * 
 * Specifically optimized for the NVIDIA Dynamo 1.0 architecture, this backend
 * leverages a disaggregated KV-Cache model to improve inference throughput 
 * and reduce compute redundancy.
 * 
 * Key Features:
 * 1. Session-Aware Registry: Tracks "Warm" vs "Cold" sessions via KVCacheRegistry.
 * 2. High-Performance gRPC: Offloads heavy computation to specialized remote services.
 */
@Service
public class DynamoBackend implements InferenceBackend {
  private static final Logger log = LoggerFactory.getLogger(DynamoBackend.class);
  private final DynamoGrpcClient dynamoClient;
  private final KVCacheRegistry cacheRegistry;

  public DynamoBackend(DynamoGrpcClient dynamoClient, KVCacheRegistry cacheRegistry) {
    this.dynamoClient = dynamoClient;
    this.cacheRegistry = cacheRegistry;
  }

  @Override
  public float infer(float value) {
    String activeSession = InferenceContext.SESSION_ID.isBound()
        ? InferenceContext.SESSION_ID.get()
        : "default-session";
    return infer(value, activeSession);
  }

  @Override
  public float infer(float value, String sessionId) {
    return infer(value, sessionId, "simple");
  }

  /**
   * Executes a session-aware inference call for a specific model.
   * Manages the "Warm-up" lifecycle for disaggregated caching before 
   * delegating the actual computation to the gRPC client.
   */
  @Override
  public float infer(float value, String sessionId, String modelName) {
    log.info("DYNAMO-EXECUTION [Session: {}, Model: {}]: Forwarding to next-gen service.", sessionId, modelName);

    if (cacheRegistry.isSessionWarm(sessionId)) {
        log.info("DYNAMO-REGISTRY: Session {} is WARM. Leveraging local KV-Cache.", sessionId);
    } else {
        log.warn("DYNAMO-REGISTRY: Session {} is COLD. Initializing disaggregated KV-Cache...", sessionId);
        cacheRegistry.markSessionActive(sessionId, "dynamo-backend-internal");
    }

    return dynamoClient.callDynamo(value, sessionId, modelName);
  }

  @Override
  public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
    return infer(value, sessionId, modelName);
  }
}
