package com.velo.sentinel;

import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.service.DynamoBridgeService;
import com.velo.sentinel.service.AdaptiveBatcher;
import com.velo.sentinel.service.ChaosComponent;
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
public class SentinelContextualRoutingTests {

    @Autowired
    private DynamoBridgeService bridgeService;

    @Autowired
    private TritonBackend tritonBackend;

    @Autowired
    private AdaptiveBatcher adaptiveBatcher;

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary public TritonBackend tritonBackend() { return mock(TritonBackend.class); }
        @Bean @Primary public AdaptiveBatcher adaptiveBatcher() { return mock(AdaptiveBatcher.class); }
        @Bean @Primary public ChaosComponent chaosComponent() { return mock(ChaosComponent.class); }
        @Bean @Primary public com.velo.sentinel.service.KVCacheRegistry kvCacheRegistry() { return mock(com.velo.sentinel.service.KVCacheRegistry.class); }
    }

    @BeforeEach
    void setup() {
        reset(tritonBackend, adaptiveBatcher);
    }

    @Test
    void testContextualRouting_LowComplexity_RoutesToTriton() {
        // Mock Triton to return 10.0
        when(tritonBackend.infer(anyFloat(), anyString(), anyString())).thenReturn(10.0f);

        // Low complexity (10 < 50) -> Should route to Triton
        float result = bridgeService.infer(5.0f, "low-comp-session", "simple", null, 10);

        assertThat(result).isEqualTo(10.0f);
        verify(tritonBackend, times(1)).infer(anyFloat(), anyString(), anyString());
        verifyNoInteractions(adaptiveBatcher); // Should NOT hit Dynamo/Batcher
    }

    @Test
    void testContextualRouting_HighComplexity_RoutesToDynamo() throws Exception {
        // Mock Dynamo/Batcher to return 0.5
        when(adaptiveBatcher.submit(anyFloat(), anyString(), anyString(), any(), anyBoolean(), any()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(0.5f));

        // High complexity (100 >= 50) -> Should route to Dynamo (default mode)
        float result = bridgeService.infer(5.0f, "high-comp-session", "simple", null, 100);

        assertThat(result).isEqualTo(0.5f);
        verify(adaptiveBatcher, times(1)).submit(anyFloat(), anyString(), anyString(), any(), anyBoolean(), any());
        verifyNoInteractions(tritonBackend); // Should NOT hit Triton directly
    }
}
