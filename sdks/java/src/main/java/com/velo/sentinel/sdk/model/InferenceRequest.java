package com.velo.sentinel.sdk.model;

/**
 * InferenceRequest: Modern Data Transfer Object for Velo-Sentinel requests.
 * 
 * @param sessionId Unique identifier for the user session, used for affinity and drift monitoring.
 * @param modelName The name of the target model (e.g., "llama-3-8b").
 * @param value The primary input value for the inference.
 * @param useAgenticOptimization Whether to enable internal routing optimizations for better latency.
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
     * Compact constructor for setting intelligent defaults.
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
    
    /**
     * Legacy constructor for simplified usage.
     * 
     * @param sessionId The session ID.
     * @param modelName The model name.
     * @param value The input value.
     * @param useAgenticOptimization Agentic flag.
     */
    public InferenceRequest(String sessionId, String modelName, float value, boolean useAgenticOptimization) {
        this(sessionId, modelName, value, useAgenticOptimization, PriorityTier.INTERACTIVE, 0, ModelPrecision.FP16);
    }
}
