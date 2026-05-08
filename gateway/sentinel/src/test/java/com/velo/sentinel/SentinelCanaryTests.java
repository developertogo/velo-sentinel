package com.velo.sentinel;

import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.service.*;
import com.velo.sentinel.model.PriorityTier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SentinelCanaryTests {

    private DynamoBridgeService bridgeService;
    private TritonBackend tritonBackend;
    private DynamoBackend dynamoBackend;
    private MeterRegistry meterRegistry;
    private DynamoResilienceComponent resilienceComponent;
    private AdaptiveBatcher adaptiveBatcher;
    private Tracer tracer;
    private RequestThrottler throttler;
    private DriftMonitor driftMonitor;
    private ChaosComponent chaosComponent;

    @BeforeEach
    void setUp() {
        tritonBackend = mock(TritonBackend.class);
        dynamoBackend = mock(DynamoBackend.class);
        meterRegistry = new SimpleMeterRegistry();
        resilienceComponent = mock(DynamoResilienceComponent.class);
        adaptiveBatcher = mock(AdaptiveBatcher.class);
        tracer = mock(Tracer.class);
        throttler = mock(RequestThrottler.class);
        driftMonitor = mock(DriftMonitor.class);
        chaosComponent = mock(ChaosComponent.class);

        // Mock throttler to execute action
        when(throttler.throttle(anyString(), any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> action = inv.getArgument(1);
            return action.get();
        });

        // Mock adaptiveBatcher.submit to return immediate result
        when(adaptiveBatcher.submit(anyFloat(), anyString(), anyString(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            float val = inv.getArgument(0);
            String session = inv.getArgument(1);
            String model = inv.getArgument(2);
            boolean isPrefill = inv.getArgument(4);
            java.util.function.Function<List<AdaptiveBatcher.BatchItem>, List<Float>> task = inv.getArgument(5);
            
            return CompletableFuture.supplyAsync(() -> {
                AdaptiveBatcher.BatchItem item = new AdaptiveBatcher.BatchItem(val, session, model, isPrefill);
                return task.apply(List.of(item)).get(0);
            });
        });

        // Mock Tracer
        io.opentelemetry.api.trace.SpanBuilder spanBuilder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(mock(io.opentelemetry.api.trace.Span.class));

        SemanticCacheService semanticCache = mock(SemanticCacheService.class);
        when(semanticCache.checkCache(anyString())).thenReturn(null);

        bridgeService = new DynamoBridgeService(
            tritonBackend, dynamoBackend, org.mockito.Mockito.mock(com.velo.sentinel.backend.MetalBackend.class), org.mockito.Mockito.mock(com.velo.sentinel.service.SpeculativeOrchestrator.class), meterRegistry, resilienceComponent, 
            adaptiveBatcher, tracer, throttler, driftMonitor, chaosComponent, mock(KVCacheRegistry.class), semanticCache
        );

        ReflectionTestUtils.setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.CANARY);
        ReflectionTestUtils.setField(bridgeService, "canaryPercentage", 20); // 20% to Dynamo
        ReflectionTestUtils.setField(bridgeService, "latencyThresholdMs", 500.0); // 500ms threshold
    }

    @Test
    void testCanaryRouting_Distribution() {
        when(tritonBackend.infer(anyFloat(), anyString(), anyString())).thenReturn(10.0f);
        when(resilienceComponent.protectedDynamoCall(anyFloat(), anyString(), anyString())).thenReturn(15.0f);

        int dynamoCount = 0;
        int tritonCount = 0;
        int totalRequests = 1000;

        for (int i = 0; i < totalRequests; i++) {
            String sessionId = "user-" + i;
            float result = bridgeService.infer(5.0f, sessionId, "simple");
            if (result == 15.0f) {
                dynamoCount++;
            } else {
                tritonCount++;
            }
        }

        double actualPercentage = (double) dynamoCount / totalRequests * 100;
        
        // With sessionId hashing, we expect it to be close to 20%
        // Given 1000 requests, it should be very close.
        assertThat(actualPercentage).isBetween(15.0, 25.0);
        
        System.out.println("Canary Stats - Dynamo: " + dynamoCount + ", Triton: " + tritonCount + ", Actual: " + actualPercentage + "%");
    }

    @Test
    void testCanaryRouting_SessionStickiness() {
        when(tritonBackend.infer(anyFloat(), anyString(), anyString())).thenReturn(10.0f);
        when(resilienceComponent.protectedDynamoCall(anyFloat(), anyString(), anyString())).thenReturn(15.0f);

        String sessionId = "consistent-user";
        
        // Multiple requests for the same session should hit the same backend
        float firstResult = bridgeService.infer(5.0f, sessionId, "simple");
        
        for (int i = 0; i < 50; i++) {
            float result = bridgeService.infer(5.0f, sessionId, "simple");
            assertThat(result).isEqualTo(firstResult);
        }
    }
}
