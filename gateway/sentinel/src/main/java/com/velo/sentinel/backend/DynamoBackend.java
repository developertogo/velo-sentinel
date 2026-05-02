package com.velo.sentinel.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.velo.sentinel.context.InferenceContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DynamoBackend: The next-gen disaggregated inference engine.
 * Specifically optimized for NVIDIA Dynamo 1.0 architecture.
 */
@Service
public class DynamoBackend implements InferenceBackend {
  private static final Logger log = LoggerFactory.getLogger(DynamoBackend.class);

  // Act as our KV-Cache Simulation "Cache Registry"
  private final Set<String> warmSessions = ConcurrentHashMap.newKeySet();

  /**
   * Overloaded method to support the legacy interface
   * while we transition to the Session-Aware model.
   */
  @Override
  public float infer(float value) {
    String activeSession = InferenceContext.SESSION_ID.isBound()
        ? InferenceContext.SESSION_ID.get()
        : "default-session";
    return infer(value, activeSession);
  }

  private final com.velo.sentinel.client.DynamoGrpcClient dynamoGrpcClient;

  public DynamoBackend(com.velo.sentinel.client.DynamoGrpcClient dynamoGrpcClient) {
    this.dynamoGrpcClient = dynamoGrpcClient;
  }

  @Override
  public float infer(float value, String sessionId) {
    log.info("DYNAMO-EXECUTION [Session: {}]: Forwarding to next-gen gRPC service.", sessionId);

    // Track "Warm vs Cold" state for simulation metrics
    if (!warmSessions.contains(sessionId)) {
      log.warn("DYNAMO-REGISTRY: Session {} is COLD. Initializing KV-Cache...", sessionId);
      warmSessions.add(sessionId);
    } else {
      log.info("DYNAMO-REGISTRY: Session {} is WARM. Leveraging local KV-Cache.", sessionId);
    }

    // Replace simulation with real gRPC call
    return dynamoGrpcClient.callDynamo(value, sessionId);
  }

  private void simulateLatency(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
