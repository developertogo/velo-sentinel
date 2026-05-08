package com.velo.sentinel;

import com.velo.sentinel.backend.DynamoBackend;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "velo.sentinel.hedging.enabled=true",
    "velo.sentinel.hedging.delay-ms=50",
    "velo.sentinel.routing-mode=DYNAMO"
})
public class SentinelHedgingTests {

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
    }

    @BeforeEach
    void setup() {
        reset(tritonBackend, adaptiveBatcher);
    }

    @Test
    void testHedging_PrimarySlow_HedgeSucceeds() throws Exception {
        // Mock Dynamo to be very slow (1 second)
        when(adaptiveBatcher.submit(anyFloat(), anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                return 0.5f;
            });
        });

        // Mock Triton to be fast (10ms)
        when(tritonBackend.infer(anyFloat(), anyString(), anyString())).thenReturn(10.0f);

        long start = System.currentTimeMillis();
        float result = bridgeService.infer(5.0f, "hedge-session", "simple");
        long duration = System.currentTimeMillis() - start;

        // Result should be from Triton (10.0) because Dynamo was hedged after 50ms
        assertThat(result).isEqualTo(10.0f);
        // Duration should be around 50ms (delay) + ~10ms (triton) = ~60ms, definitely < 500ms
        assertThat(duration).isLessThan(500);

        verify(tritonBackend, times(1)).infer(anyFloat(), anyString(), anyString());
    }
}
