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
     * Checks if a session is currently 'Warm'.
     * Returns false if Redis is unavailable (Fail-Open to Cold Start).
     */
    public boolean isSessionWarm(String sessionId) {
        if (sessionId == null || sessionId.equals("anonymous")) {
            return false;
        }

        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + sessionId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("REDIS-UNAVAILABLE: Assuming session {} is COLD. Reason: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Marks a session as active and refreshes its TTL.
     */
    public void markSessionActive(String sessionId) {
        if (sessionId == null || sessionId.equals("anonymous")) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, "WARM", SESSION_TTL);
        } catch (Exception e) {
            log.error("REDIS-WRITE-FAILURE: Failed to mark session {} as active.", sessionId);
        }
    }
}
