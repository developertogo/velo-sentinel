package com.velo.sentinel.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class KVCacheRegistryTests {

    private static RedisServer redisServer;
    private KVCacheRegistry cacheRegistry;
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() {
        // Try a random port if 6379 is taken, but for this demo 6379 is expected
        redisServer = new RedisServer(6379);
        try {
            redisServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start Embedded Redis: " + e.getMessage());
        }
    }

    @AfterAll
    static void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setup() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        cacheRegistry = new KVCacheRegistry(redisTemplate);
        
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            // Ignore if redis isn't actually running
        }
    }

    @Test
    void testSessionWarmthLifecycle() {
        if (redisServer == null || !redisServer.isActive()) return;

        String sessionId = "test-user-01";
        assertThat(cacheRegistry.isSessionWarm(sessionId)).isFalse();
        cacheRegistry.markSessionActive(sessionId, "node-1");
        assertThat(cacheRegistry.isSessionWarm(sessionId)).isTrue();
    }

    @Test
    void testAnonymousSessionIsAlwaysCold() {
        assertThat(cacheRegistry.isSessionWarm("anonymous")).isFalse();
        cacheRegistry.markSessionActive("anonymous", "node-1");
        assertThat(cacheRegistry.isSessionWarm("anonymous")).isFalse();
    }

    @Test
    void testGracefulDegradationOnRedisFailure() {
        // Use a mock template to simulate failure without stopping the real embedded server
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        when(mockTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis Connection Refused"));
        
        KVCacheRegistry failureRegistry = new KVCacheRegistry(mockTemplate);
        
        // Should return false (COLD) instead of throwing exception
        assertThat(failureRegistry.isSessionWarm("any-session")).isFalse();
    }

    @Test
    void testNullSessionIdIsAlwaysCold() {
        assertThat(cacheRegistry.isSessionWarm(null)).isFalse();
        cacheRegistry.markSessionActive(null, "node-1");
        assertThat(cacheRegistry.isSessionWarm(null)).isFalse();
    }

    @Test
    void testRedisWriteFailure() {
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> mockOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(mockTemplate.opsForValue()).thenReturn(mockOps);
        doThrow(new RuntimeException("Write Failure")).when(mockOps).set(anyString(), anyString(), any(java.time.Duration.class));

        KVCacheRegistry failureRegistry = new KVCacheRegistry(mockTemplate);
        
        // This should not throw an exception
        failureRegistry.markSessionActive("some-session", "node-1");
    }

    @Test
    void testSessionWarmthSuccessMock() {
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> mockOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(mockTemplate.opsForValue()).thenReturn(mockOps);
        when(mockOps.get(anyString())).thenReturn("node-1");
        
        KVCacheRegistry mockRegistry = new KVCacheRegistry(mockTemplate);
        assertThat(mockRegistry.isSessionWarm("test-session")).isTrue();
    }

    @Test
    void testGetWorkerAffinity() {
        if (redisServer == null || !redisServer.isActive()) return;

        String sessionId = "test-affinity-user";
        assertThat(cacheRegistry.getWorkerAffinity(sessionId)).isNull();
        
        cacheRegistry.markSessionActive(sessionId, "gpu-worker-42");
        assertThat(cacheRegistry.getWorkerAffinity(sessionId)).isEqualTo("gpu-worker-42");
    }
}
