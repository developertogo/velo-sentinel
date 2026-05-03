package com.velo.sentinel.model;

/**
 * Modern DTO for Velo-Sentinel requests.
 * Records are immutable, making them thread-safe for our Virtual Threads.
 * Includes modelName for dynamic multi-model routing.
 */
public record InferenceRequest(
    String sessionId,
    String modelName,
    float value,
    boolean useAgenticOptimization
) {
    // Provide a canonical constructor or a secondary one for convenience if needed,
    // but the record provides the main one.
}