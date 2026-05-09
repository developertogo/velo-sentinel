package com.velo.sentinel.sdk.model;

/**
 * InferenceResponse: The Standardized Output DTO for Velo-Sentinel.
 * 
 * @param sessionId The original session ID to allow for request/response pairing.
 * @param prediction The float result returned by the inference model.
 * @param status Execution status (SUCCESS, FAILURE, CIRCUIT_OPEN, etc.).
 */
public record InferenceResponse(
    String sessionId,
    float prediction,
    Status status
) {
    /**
     * Execution status of the inference request.
     */
    public enum Status {
        /** Request completed successfully. */
        SUCCESS,
        /** General backend failure. */
        FAILURE,
        /** Circuit breaker is currently open. */
        CIRCUIT_OPEN,
        /** The backend service is unreachable. */
        BACKEND_OUTAGE,
        /** Request was dropped to satisfy SLA requirements. */
        SLA_VIOLATED
    }
}
