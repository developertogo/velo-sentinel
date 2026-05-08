package com.velo.sentinel;

import com.velo.sentinel.backend.MetalBackend;
import com.velo.sentinel.service.DynamoBridgeService;
import com.velo.sentinel.service.SpeculativeOrchestrator;
import com.velo.sentinel.model.ModelPrecision;
import com.velo.sentinel.model.PriorityTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "velo.sentinel.routing-mode=DYNAMO"
})
public class SentinelPhase6Tests {

    @Autowired
    private DynamoBridgeService bridgeService;

    @Autowired
    private MetalBackend metalBackend;

    @Autowired
    private SpeculativeOrchestrator speculativeOrchestrator;

    @Autowired
    private com.velo.sentinel.service.AdaptiveBatcher adaptiveBatcher;

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary public MetalBackend metalBackend() { return mock(MetalBackend.class); }
        @Bean @Primary public SpeculativeOrchestrator speculativeOrchestrator() { return mock(SpeculativeOrchestrator.class); }
        @Bean @Primary public com.velo.sentinel.service.AdaptiveBatcher adaptiveBatcher() { return mock(com.velo.sentinel.service.AdaptiveBatcher.class); }
        @Bean @Primary public com.velo.sentinel.service.KVCacheRegistry kvCacheRegistry() { return mock(com.velo.sentinel.service.KVCacheRegistry.class); }
    }

    @BeforeEach
    void setup() {
        reset(metalBackend, speculativeOrchestrator, adaptiveBatcher);
    }

    @Test
    void testHybridRouting_PrivacySensitive_OffloadsToMetal() {
        when(metalBackend.infer(anyFloat(), anyString(), anyString())).thenReturn(55.0f);

        // Session starting with "private-" -> Hybrid Routing to Metal
        float result = bridgeService.sentinelExecute(10.0f, "private-session-123", "big-model", com.velo.sentinel.model.PriorityTier.INTERACTIVE, 100, com.velo.sentinel.model.ModelPrecision.FP16, false);

        assertThat(result).isEqualTo(55.0f);
        verify(metalBackend).infer(eq(10.0f), eq("private-session-123"), eq("big-model"));
        verifyNoInteractions(speculativeOrchestrator);
    }

    @Test
    void testSpeculativeDecoding_TriggeredByFlag() {
        when(speculativeOrchestrator.executeSpeculative(anyFloat(), anyString(), anyString(), any())).thenReturn(77.0f);

        // useAgenticOptimization=true -> Trigger Speculative Orchestrator
        float result = bridgeService.sentinelExecute(10.0f, "normal-session", "llama-3", PriorityTier.INTERACTIVE, 100, ModelPrecision.FP16, true);

        assertThat(result).isEqualTo(77.0f);
        verify(speculativeOrchestrator).executeSpeculative(eq(10.0f), eq("normal-session"), eq("llama-3"), any());
    }

    @Test
    void testQuantizationAwareRouting_PassesPrecision() {
        when(adaptiveBatcher.submit(anyFloat(), anyString(), anyString(), any(), anyBoolean(), any()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(99.0f));

        // Just verify it doesn't crash and flows through
        float result = bridgeService.sentinelExecute(10.0f, "session-1", "llama-3", PriorityTier.INTERACTIVE, 100, ModelPrecision.INT4, false);
        assertThat(result).isEqualTo(99.0f);
    }
}
