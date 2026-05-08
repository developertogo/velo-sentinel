package com.velo.sentinel.controller;

import com.velo.sentinel.service.AdaptiveBatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SentinelScalingController: The "Brain" for the Dynamo-Aware K8s Operator.
 * 
 * Exposes real-time backend pressure and predictive scaling signals.
 * A custom K8s Operator queries this to perform preemptive GPU rebalancing.
 */
@RestController
@RequestMapping("/actuator/velo")
public class SentinelScalingController {

    private final AdaptiveBatcher adaptiveBatcher;

    public SentinelScalingController(AdaptiveBatcher adaptiveBatcher) {
        this.adaptiveBatcher = adaptiveBatcher;
    }

    @GetMapping("/scaling-metrics")
    public Map<String, Object> getScalingMetrics() {
        double concurrencyScore = adaptiveBatcher.getConcurrencyScore();
        double backpressureFactor = adaptiveBatcher.getBackpressureFactor();
        
        return Map.of(
            "status", "HEALTHY",
            "metrics", Map.of(
                "concurrency_score", concurrencyScore,
                "backpressure_factor", backpressureFactor,
                "recommended_replicas", backpressureFactor > 5.0 ? 10 : 5,
                "strategy", "PREDICTIVE_GPU_REBALANCING"
            )
        );
    }
}
