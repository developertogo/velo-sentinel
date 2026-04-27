package com.velo.sentinel.model;

/**
 * Modern DTO for Velo-Sentinel requests.
 * Records are immutable, making them thread-safe for our Virtual Threads.
 */
public record InferenceRequest(
    String sessionId,
    float value,
    boolean useAgenticOptimization) {
}