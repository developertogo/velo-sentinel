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
    public org.springframework.http.ResponseEntity<InferenceResponse> infer(@RequestBody InferenceRequest request) {
        String model = request.modelName() != null ? request.modelName() : "simple";
        String sessionId = request.sessionId() != null ? request.sessionId() : "anonymous";
        
        try {
            return com.velo.sentinel.context.InferenceContext.runInContext(sessionId, () -> {
                float result = bridgeService.infer(request.value(), sessionId, model, request.priority());
                return org.springframework.http.ResponseEntity.ok(new InferenceResponse(
                    sessionId,
                    result,
                    InferenceResponse.Status.SUCCESS
                ));
            });
        } catch (io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
            org.slf4j.LoggerFactory.getLogger(InferenceController.class)
                .warn("SLA-VIOLATION: Session {} throttled. Reason: {}", sessionId, e.getMessage());
            
            return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .body(new InferenceResponse(
                    sessionId,
                    0.0f,
                    InferenceResponse.Status.SLA_VIOLATED
                ));
        } catch (Exception e) {
            // Log the critical outage
            org.slf4j.LoggerFactory.getLogger(InferenceController.class)
                .error("CRITICAL-OUTAGE: Total system failure. Reason: {}", e.getMessage());
            
            return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                .body(new InferenceResponse(
                    sessionId,
                    0.0f,
                    InferenceResponse.Status.BACKEND_OUTAGE
                ));
        }
    }
}
