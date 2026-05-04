package com.velo.sentinel.controller;

import com.velo.sentinel.model.InferenceRequest;
import com.velo.sentinel.model.InferenceResponse;
import com.velo.sentinel.service.DynamoBridgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InferenceController: The Primary Gateway Interface.
 * 
 * Provides a standardized REST API for triggering inference workflows.
 * Bridges traditional JSON/HTTP clients to our optimized gRPC/Virtual-Thread backend.
 * Supports legacy single-inference and next-gen dual-inference (Shadow) modes.
 */
@RestController
@RequestMapping("/infer")
public class InferenceController {

    private final DynamoBridgeService bridgeService;

    public InferenceController(DynamoBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Main inference endpoint.
     * 
     * @param request The inference request containing input values and session state.
     * @return InferenceResponse with prediction results and execution status.
     */
    @PostMapping
    public InferenceResponse infer(@RequestBody InferenceRequest request) {
        // Delegate to the bridge service which handles routing and shadow validation
        String model = request.modelName() != null ? request.modelName() : "simple";
        float result = bridgeService.infer(request.value(), request.sessionId(), model, request.priority());
        
        return new InferenceResponse(
            request.sessionId() != null ? request.sessionId() : "anonymous",
            result,
            InferenceResponse.Status.SUCCESS
        );
    }
}
