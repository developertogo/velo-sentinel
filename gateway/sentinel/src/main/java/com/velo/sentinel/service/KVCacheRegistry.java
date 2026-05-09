package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * KVCacheRegistry: The Global Session Registry for Disaggregated Inference.
 * 
 * Tracks whether a session's KV-Cache is currently "Warm" (active on a GPU node)
 * or "Cold" (evicted/stale).
 */
@Service
public class KVCacheRegistry {
    private static final Logger log = LoggerFactory.getLogger(KVCacheRegistry.class);
    private static final String KEY_PREFIX = "sentinel:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(10); // Sessions stay 'Warm' for 10 mins

    private final StringRedisTemplate redisTemplate;

    /**
     * Initializes the registry with a Redis template.
     * 
     * @param redisTemplate The template for interacting with the Redis backing store.
     */
    public KVCacheRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks which worker node hosts the session's KV-Cache.
     * 
     * @param sessionId The identifier for the user session.
     * @return The ID of the worker node where the cache is located, or {@code null} if the session is Cold.
     */
    public String getWorkerAffinity(String sessionId) {
        if (sessionId == null || sessionId.equals("anonymous")) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("REDIS-UNAVAILABLE: Cannot determine affinity for {}.", sessionId);
            return null;
        }
    }

    /**
     * Determines if a session has an active (Warm) KV-Cache.
     * 
     * @param sessionId The session to check.
     * @return {@code true} if the session is warm, {@code false} otherwise.
     */
    public boolean isSessionWarm(String sessionId) {
        return getWorkerAffinity(sessionId) != null;
    }

    /**
     * Marks a session as active on a specific worker node.
     * Sets a TTL to ensure inactive sessions eventually transition back to Cold.
     * 
     * @param sessionId The session identifier.
     * @param workerNodeId The ID of the node currently hosting the session's state.
     */
    public void markSessionActive(String sessionId, String workerNodeId) {
        if (sessionId == null || sessionId.equals("anonymous")) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, workerNodeId, SESSION_TTL);
        } catch (Exception e) {
            log.error("REDIS-WRITE-FAILURE: Failed to mark session {} on node {}.", sessionId, workerNodeId);
        }
    }
}
