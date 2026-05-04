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
    boolean useAgenticOptimization,
    PriorityTier priority
) {
    public InferenceRequest {
        if (priority == null) {
            priority = PriorityTier.INTERACTIVE; // Default to interactive if not provided
        }
    }
    
    // Legacy constructor for existing tests/calls
    public InferenceRequest(String sessionId, String modelName, float value, boolean useAgenticOptimization) {
        this(sessionId, modelName, value, useAgenticOptimization, PriorityTier.INTERACTIVE);
    }
}