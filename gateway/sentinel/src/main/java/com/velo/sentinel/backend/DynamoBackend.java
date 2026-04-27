package com.velo.sentinel.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.velo.sentinel.service.DynamoBridgeService;

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
    String activeSession = DynamoBridgeService.SESSION_ID.isBound()
        ? DynamoBridgeService.SESSION_ID.get()
        : "default-session";
    return infer(value, activeSession);
  }

  @Override
  public float infer(float value, String sessionId) {
    log.info("DYNAMO-EXECUTION: Processing task for Session: {} | Value: {}", sessionId, value);

    if (warmSessions.contains(sessionId)) {
      log.info("DYNAMO-EXECUTION [CACHE-HIT]: Session {} found. Fast Decode Path.", sessionId);
      simulateLatency(10);
    } else {
      log.info("DYNAMO-EXECUTION [CACHE-MISS]: Session {} new. Prefill & Cache store.", sessionId);
      warmSessions.add(sessionId);
      simulateLatency(50);
    }
    return value + 0.5f;
  }

  private void simulateLatency(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
