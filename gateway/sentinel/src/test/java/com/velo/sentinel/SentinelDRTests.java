package com.velo.sentinel;

import com.velo.sentinel.backend.StandbyBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.backend.MetalBackend;
import com.velo.sentinel.client.StandbyTritonClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.service.*;
import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SentinelDRTests {

    private StandbyBackend standbyBackend;
    private StandbyTritonClient standbyClient;
    private DynamoBridgeService bridgeService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        standbyClient = mock(StandbyTritonClient.class);
        standbyBackend = new StandbyBackend(standbyClient);
        
        TritonBackend tritonBackend = mock(TritonBackend.class);
        DynamoBackend dynamoBackend = mock(DynamoBackend.class);
        MetalBackend metalBackend = mock(MetalBackend.class);
        SpeculativeOrchestrator speculativeOrchestrator = mock(SpeculativeOrchestrator.class);
        meterRegistry = new SimpleMeterRegistry();
        DynamoResilienceComponent resilienceComponent = mock(DynamoResilienceComponent.class);
        AdaptiveBatcher adaptiveBatcher = mock(AdaptiveBatcher.class);
        Tracer tracer = mock(Tracer.class);
        RequestThrottler throttler = mock(RequestThrottler.class);
        DriftMonitor driftMonitor = mock(DriftMonitor.class);
        ChaosComponent chaosComponent = mock(ChaosComponent.class);
        KVCacheRegistry kvCacheRegistry = mock(KVCacheRegistry.class);
        SemanticCacheService semanticCache = mock(SemanticCacheService.class);
        PrivacyScrubberService privacyScrubber = mock(PrivacyScrubberService.class);
        AuditLoggerService auditLogger = mock(AuditLoggerService.class);

        // Mock Tracer to avoid NPEs
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        Span mockSpan = mock(Span.class);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);

        // Mock throttler to pass through calls
        when(throttler.throttle(anyString(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> task = invocation.getArgument(1);
            return task.get();
        });

        when(semanticCache.checkCache(any())).thenReturn(null);

        bridgeService = new DynamoBridgeService(
                tritonBackend, dynamoBackend, metalBackend, speculativeOrchestrator,
                meterRegistry, resilienceComponent, adaptiveBatcher, tracer,
                throttler, driftMonitor, chaosComponent, kvCacheRegistry,
                semanticCache, privacyScrubber, auditLogger, standbyBackend
        );

        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.FAILOVER);
        setField(bridgeService, "hedgingEnabled", false);
    }

    @Test
    void testFailoverToStandbyRegion() {
        // Setup standby mock response
        float expectedValue = 99.9f;
        byte[] resultBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(expectedValue).array();
        ModelInferResponse mockResponse = ModelInferResponse.newBuilder()
                .addRawOutputContents(ByteString.copyFrom(resultBytes))
                .build();
        
        when(standbyClient.infer(anyFloat(), anyString())).thenReturn(mockResponse);

        // Execute inference in FAILOVER mode
        float result = bridgeService.infer(5.0f, "dr-test-session", "simple-model");

        // Verify result comes from standby region
        assertThat(result).isEqualTo(expectedValue);
        verify(standbyClient).infer(eq(5.0f), eq("simple-model"));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
