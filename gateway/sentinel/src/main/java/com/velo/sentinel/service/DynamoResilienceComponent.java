package com.velo.sentinel.service;

import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.backend.TritonBackend;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DynamoResilienceComponent: Isolated resilience layer for Dynamo.
 * Handles circuit breaking and fallback logic outside of the main bridge service
 * to ensure Spring AOP proxying works reliably.
 */
@Component
public class DynamoResilienceComponent {
    private static final Logger log = LoggerFactory.getLogger(DynamoResilienceComponent.class);
    private final DynamoBackend dynamoBackend;
    private final TritonBackend tritonBackend;

    /**
     * Initializes the resilience component with both next-gen and legacy backends.
     * 
     * @param dynamoBackend The primary disaggregated backend.
     * @param tritonBackend The fallback legacy backend.
     */
    public DynamoResilienceComponent(DynamoBackend dynamoBackend, TritonBackend tritonBackend) {
        this.dynamoBackend = dynamoBackend;
        this.tritonBackend = tritonBackend;
    }

    /**
     * Executes a protected call to Dynamo for a specific model.
     * Trips the circuit breaker if failures exceed the threshold.
     * Fails open to Triton if the Dynamo path is unhealthy.
     * 
     * @param value The input float value.
     * @param sessionId The session identifier.
     * @param modelName The target model name.
     * @return The result from Dynamo (or Triton if fallback).
     */
    @CircuitBreaker(name = "dynamoBackend", fallbackMethod = "failOpenToTriton")
    public float protectedDynamoCall(float value, String sessionId, String modelName) {
        return dynamoBackend.infer(value, sessionId, modelName);
    }

    /**
     * Resilience4j Fallback Handler.
     * Invoked when the circuit is open or a call fails, ensuring graceful degradation.
     * 
     * @param value The original input value.
     * @param sessionId The session identifier.
     * @param modelName The target model.
     * @param t The exception that triggered the fallback.
     * @return The result from the fallback Triton backend.
     */
    public float failOpenToTriton(float value, String sessionId, String modelName, Throwable t) {
        log.error("DYNAMO-FAILURE [Model: {}]: Circuit is OPEN or Call Failed. Reason: {}. Failing open to Triton.",
            modelName, t.getMessage());
        return tritonBackend.infer(value, sessionId, modelName);
    }
}
