package com.velo.sentinel.model;

/**
 * InferenceResponse: The Standardized Output DTO.
 * 
 * Used to return prediction results and execution metadata to the caller.
 * Designed for clean JSON serialization and Java 25 record efficiency.
 */
public record InferenceResponse(
    String sessionId,
    float prediction,
    Status status
) {
    public enum Status {
        SUCCESS,
        FAILURE,
        CIRCUIT_OPEN
    }
}
