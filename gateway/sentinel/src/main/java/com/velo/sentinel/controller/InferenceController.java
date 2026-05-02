package com.velo.sentinel.controller;

import com.velo.sentinel.model.InferenceRequest;
import com.velo.sentinel.model.InferenceResponse;
import com.velo.sentinel.service.DynamoBridgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * InferenceController: The Primary Gateway Interface.
 * 
 * Provides a standardized REST API for triggering inference workflows.
 * Bridges traditional JSON/HTTP clients to our optimized gRPC/Virtual-Thread backend.
 * 
 * Performance Note: This controller is non-blocking and fully compatible 
 * with Java 25 Virtual Threads.
 */
@RestController
public class InferenceController {

    private final DynamoBridgeService bridgeService;

    public InferenceController(DynamoBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Executes an inference request via the mission-critical Sentinel bridge.
     * 
     * @param request The JSON payload containing input values and session context.
     * @return A response DTO containing the prediction result and execution metadata.
     */
    @PostMapping("/infer")
    public InferenceResponse infer(@RequestBody InferenceRequest request) {
        // Delegate to the bridge service which handles routing and shadow validation
        float result = bridgeService.infer(request.value(), request.sessionId());
        
        return new InferenceResponse(
            request.sessionId() != null ? request.sessionId() : "anonymous",
            result,
            "SUCCESS"
        );
    }
}
