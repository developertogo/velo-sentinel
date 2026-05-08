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

    public KVCacheRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks which worker node hosts the session's KV-Cache.
     * Returns null if the session is Cold.
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

    public boolean isSessionWarm(String sessionId) {
        return getWorkerAffinity(sessionId) != null;
    }

    /**
     * Marks a session as active on a specific worker node.
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
