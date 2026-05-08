package com.velo.sentinel.controller;

import com.velo.sentinel.service.AdaptiveBatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SentinelScalingControllerTests {

    private SentinelScalingController controller;
    private AdaptiveBatcher adaptiveBatcher;

    @BeforeEach
    void setUp() {
        adaptiveBatcher = mock(AdaptiveBatcher.class);
        controller = new SentinelScalingController(adaptiveBatcher);
    }

    @Test
    void testGetScalingMetrics_ReturnsHealthyStatus() {
        when(adaptiveBatcher.getConcurrencyScore()).thenReturn(0.5);
        when(adaptiveBatcher.getBackpressureFactor()).thenReturn(1.2);
        
        Map<String, Object> response = controller.getScalingMetrics();
        
        assertThat(response.get("status")).isEqualTo("HEALTHY");
        
        Map<String, Object> metrics = (Map<String, Object>) response.get("metrics");
        assertThat(metrics.get("backpressure_factor")).isEqualTo(1.2);
    }
}
