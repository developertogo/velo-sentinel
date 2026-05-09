package com.velo.sentinel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticCacheServiceTests {

    private final SemanticCacheService cache = new SemanticCacheService();

    @Test
    void testCacheHit() {
        String prompt = "What is the capital of France?";
        float result = 42.0f;
        cache.updateCache(prompt, result);
        
        Float cachedValue = cache.checkCache(prompt);
        assertNotNull(cachedValue);
        assertEquals(result, cachedValue);
    }

    @Test
    void testCacheMiss() {
        String prompt = "Who won the world cup in 2022?";
        assertNull(cache.checkCache(prompt));
    }

    @Test
    void testEviction() {
        String prompt = "Temporary data";
        cache.updateCache(prompt, 1.0f);
        assertNotNull(cache.checkCache(prompt));
        
        cache.evict(prompt);
        assertNull(cache.checkCache(prompt));
    }
}
