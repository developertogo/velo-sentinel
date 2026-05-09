package com.velo.sentinel.controller;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.client.TritonGrpcClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HealthController: Custom Readiness Probes for Kubernetes.
 * 
 * Provides a dedicated endpoint for orchestrators to verify gateway readiness.
 * Checks connectivity to all critical inference backends before signaling UP.
 */
@RestController
public class HealthController {

    private final TritonGrpcClient tritonClient;
    private final DynamoGrpcClient dynamoClient;

    /**
     * Initializes the controller with necessary backend clients.
     * 
     * @param tritonClient The legacy Triton gRPC client.
     * @param dynamoClient The disaggregated Dynamo gRPC client.
     */
    public HealthController(TritonGrpcClient tritonClient, DynamoGrpcClient dynamoClient) {
        this.tritonClient = tritonClient;
        this.dynamoClient = dynamoClient;
    }

    /**
     * Aggregated health check for Kubernetes readiness probes.
     * 
     * @return A map containing the status of all critical backends.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean tritonHealthy = tritonClient.checkHealth();
        boolean dynamoHealthy = dynamoClient.checkHealth();

        boolean allHealthy = tritonHealthy && dynamoHealthy;

        Map<String, String> details = Map.of(
            "status", allHealthy ? "UP" : "DOWN",
            "triton", tritonHealthy ? "UP" : "DOWN",
            "dynamo", dynamoHealthy ? "UP" : "DOWN"
        );

        if (allHealthy) {
            return ResponseEntity.ok(details);
        } else {
            return ResponseEntity.status(503).body(details);
        }
    }
}
