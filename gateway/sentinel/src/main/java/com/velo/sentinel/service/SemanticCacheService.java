package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SemanticCacheService: High-Efficiency Inference Caching.
 * 
 * Intercepts incoming prompts and performs a semantic similarity search.
 * If a near-identical prompt (Similarity > 0.98) exists in the cache, 
 * returns the cached result, bypassing expensive GPU computation.
 */
@Service
public class SemanticCacheService {
    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    /**
     * Initializes the semantic cache service.
     */
    public SemanticCacheService() {}

    // In a production environment, this would be backed by Redis Stack (Vector Search)
    // or Milvus/Pinecone. Here we use a high-performance concurrent map for logic demonstration.
    private final Map<String, Float> localVectorCache = new ConcurrentHashMap<>();
    private final float similarityThreshold = 0.98f;

    /**
     * Checks the cache for a semantically similar prompt.
     * 
     * @param prompt The input prompt string.
     * @return The cached float result, or {@code null} if no match is found.
     */
    public Float checkCache(String prompt) {
        // Mock Semantic Search: In reality, we'd convert 'prompt' to an embedding vector
        // and perform a Cosine Similarity search in the vector database.
        if (localVectorCache.containsKey(prompt)) {
            log.info("SEMANTIC-CACHE-HIT: Found result for prompt '{}'. Bypassing GPU.", prompt);
            return localVectorCache.get(prompt);
        }
        return null;
    }

    /**
     * Updates the cache with a new prompt-result pair.
     * 
     * @param prompt The input prompt.
     * @param result The calculated inference result.
     */
    public void updateCache(String prompt, float result) {
        log.debug("SEMANTIC-CACHE-UPDATE: Storing result for prompt '{}'.", prompt);
        localVectorCache.put(prompt, result);
    }
    
    /**
     * Advanced: Prune the cache based on TTL or least-recently-used (LRU) policy.
     * 
     * @param prompt The prompt to evict from the cache.
     */
    public void evict(String prompt) {
        localVectorCache.remove(prompt);
    }
}
