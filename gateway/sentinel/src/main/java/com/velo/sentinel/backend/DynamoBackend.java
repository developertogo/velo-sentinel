package com.velo.sentinel.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.velo.sentinel.context.InferenceContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DynamoBackend: The Next-Gen Disaggregated Inference Engine.
 * 
 * Specifically optimized for the NVIDIA Dynamo 1.0 architecture, this backend
 * leverages a disaggregated KV-Cache model to improve inference throughput 
 * and reduce compute redundancy.
 * 
 * Key Features:
 * 1. Session-Aware Registry: Tracks "Warm" vs "Cold" sessions to simulate KV-Cache affinity.
 * 2. High-Performance gRPC: Offloads heavy computation to specialized remote services.
 * 3. Thread-Safe State: Uses ConcurrentHashMaps to manage session state safely across Virtual Threads.
 */
@Service
public class DynamoBackend implements InferenceBackend {
  private static final Logger log = LoggerFactory.getLogger(DynamoBackend.class);

  /**
   * Acts as our disaggregated "Cache Registry". 
   * Tracks sessions that have already initialized their remote state.
   */
  private final Set<String> warmSessions = ConcurrentHashMap.newKeySet();

  private final com.velo.sentinel.client.DynamoGrpcClient dynamoGrpcClient;

  public DynamoBackend(com.velo.sentinel.client.DynamoGrpcClient dynamoGrpcClient) {
    this.dynamoGrpcClient = dynamoGrpcClient;
  }

  /**
   * Overloaded method to support the legacy interface while transitioning 
   * to the Session-Aware model. Automatically recovers the sessionId from 
   * the active ScopedValue context.
   */
  @Override
  public float infer(float value) {
    String activeSession = InferenceContext.SESSION_ID.isBound()
        ? InferenceContext.SESSION_ID.get()
        : "default-session";
    return infer(value, activeSession);
  }

  /**
   * Executes a session-aware inference call.
   * Manages the "Warm-up" lifecycle for disaggregated caching before 
   * delegating the actual computation to the gRPC client.
   * 
   * @param value The input feature value.
   * @param sessionId The unique session identifier used for cache routing.
   * @return The prediction result from the Dynamo service.
   */
  @Override
  public float infer(float value, String sessionId) {
    log.info("DYNAMO-EXECUTION [Session: {}]: Forwarding to next-gen gRPC service.", sessionId);

    // Lifecycle Management: Track "Warm vs Cold" state for simulation observability
    if (!warmSessions.contains(sessionId)) {
      log.warn("DYNAMO-REGISTRY: Session {} is COLD. Initializing disaggregated KV-Cache...", sessionId);
      warmSessions.add(sessionId);
    } else {
      log.info("DYNAMO-REGISTRY: Session {} is WARM. Leveraging established cache state.", sessionId);
    }

    // Delegate to the hardened gRPC client
    return dynamoGrpcClient.callDynamo(value, sessionId);
  }
}
