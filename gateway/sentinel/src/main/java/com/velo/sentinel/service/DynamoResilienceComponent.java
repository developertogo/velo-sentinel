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

    public DynamoResilienceComponent(DynamoBackend dynamoBackend, TritonBackend tritonBackend) {
        this.dynamoBackend = dynamoBackend;
        this.tritonBackend = tritonBackend;
    }

    /**
     * Executes a protected call to Dynamo.
     * Trips the circuit breaker if failures exceed the threshold.
     * Fails open to Triton if the Dynamo path is unhealthy.
     */
    @CircuitBreaker(name = "dynamoBackend", fallbackMethod = "failOpenToTriton")
    public float protectedDynamoCall(float value) {
        return dynamoBackend.infer(value);
    }

    /**
     * Resilience4j Fallback Handler.
     */
    public float failOpenToTriton(float value, Throwable t) {
        log.error("DYNAMO-FAILURE: Circuit is OPEN or Call Failed. Reason: {}. Failing open to Triton.",
            t.getMessage());
        return tritonBackend.infer(value);
    }
}
