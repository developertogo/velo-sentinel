package com.velo.sentinel.model;

/**
 * InferenceRequest: Modern Data Transfer Object for Velo-Sentinel.
 * 
 * @param sessionId Unique identifier for the user session.
 * @param modelName The name of the target model (e.g., "llama-3-8b").
 * @param value The primary input value for the inference.
 * @param useAgenticOptimization Whether to enable internal routing optimizations.
 * @param priority The priority tier (REALTIME, INTERACTIVE, BACKGROUND).
 * @param complexity Estimated complexity score (0-10) for load balancing.
 * @param precision Requested quantization level (FP16, INT8, INT4).
 */
public record InferenceRequest(
    String sessionId,
    String modelName,
    float value,
    boolean useAgenticOptimization,
    PriorityTier priority,
    Integer complexity,
    ModelPrecision precision
) {
    /**
     * Canonical constructor with default value logic.
     */
    public InferenceRequest {
        if (priority == null) {
            priority = PriorityTier.INTERACTIVE; 
        }
        if (complexity == null) {
            complexity = 0;
        }
        if (precision == null) {
            precision = ModelPrecision.FP16; // Default to FP16
        }
    }
    
    // Legacy constructor for existing tests/calls
    /**
     * Legacy constructor for existing tests and simple calls.
     * 
     * @param sessionId Session ID.
     * @param modelName Model name.
     * @param value Input value.
     * @param useAgenticOptimization Agentic flag.
     */
    public InferenceRequest(String sessionId, String modelName, float value, boolean useAgenticOptimization) {
        this(sessionId, modelName, value, useAgenticOptimization, PriorityTier.INTERACTIVE, 0, ModelPrecision.FP16);
    }
}