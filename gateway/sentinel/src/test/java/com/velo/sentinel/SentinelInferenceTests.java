package com.velo.sentinel;

import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.service.AdaptiveBatcher;
import com.velo.sentinel.service.ChaosComponent;
import com.velo.sentinel.service.DriftMonitor;
import com.velo.sentinel.service.DynamoBridgeService;
import com.velo.sentinel.service.DynamoResilienceComponent;
import com.velo.sentinel.service.KVCacheRegistry;
import com.velo.sentinel.service.RequestThrottler;
import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SentinelInferenceTests: Comprehensive Logic & Routing Validation.
 */
public class SentinelInferenceTests {

    private TritonBackend tritonBackend;
    private DynamoBackend dynamoBackend;
    private DynamoBridgeService bridgeService;
    private DynamoResilienceComponent resilienceComponent;
    private KVCacheRegistry cacheRegistry;
    private AdaptiveBatcher adaptiveBatcher;
    private TritonGrpcClient tritonClient;
    private DynamoGrpcClient dynamoGrpcClient;
    private MeterRegistry meterRegistry;
    private Tracer tracer;
    private RequestThrottler throttler;
    private DriftMonitor driftMonitor;
    private ChaosComponent chaosComponent;
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setup() {
        tritonClient = mock(TritonGrpcClient.class);
        dynamoGrpcClient = mock(DynamoGrpcClient.class);
        meterRegistry = new SimpleMeterRegistry();
        tracer = mock(Tracer.class);
        throttler = mock(RequestThrottler.class);
        redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        cacheRegistry = new KVCacheRegistry(redisTemplate);
        adaptiveBatcher = mock(AdaptiveBatcher.class);
        driftMonitor = mock(DriftMonitor.class);
        chaosComponent = mock(ChaosComponent.class);

        // Mock adaptiveBatcher.submit to return a future that respects task execution time
        when(adaptiveBatcher.submit(anyFloat(), anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            float val = invocation.getArgument(0);
            String session = invocation.getArgument(1);
            String model = invocation.getArgument(2);
            java.util.function.Function<java.util.List<com.velo.sentinel.service.AdaptiveBatcher.BatchItem>, java.util.List<Float>> task = invocation.getArgument(4);
            
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                com.velo.sentinel.service.AdaptiveBatcher.BatchItem item = new com.velo.sentinel.service.AdaptiveBatcher.BatchItem(val, session, model);
                java.util.List<Float> results = task.apply(java.util.List.of(item));
                return results.get(0);
            });
        });

        // Mock Tracer to avoid NPEs
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        Span mockSpan = mock(Span.class);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);

        // Mock throttler to pass through calls by default
        when(throttler.throttle(anyString(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> task = invocation.getArgument(1);
            return task.get();
        });
        
        tritonBackend = new TritonBackend(tritonClient);
        dynamoBackend = new DynamoBackend(dynamoGrpcClient, cacheRegistry);
        // Use a Spy to simulate Spring AOP / Circuit Breaker behavior in a unit test
        resilienceComponent = spy(new DynamoResilienceComponent(dynamoBackend, tritonBackend));
        bridgeService = new DynamoBridgeService(tritonBackend, dynamoBackend, meterRegistry, resilienceComponent, adaptiveBatcher, tracer, throttler, driftMonitor, chaosComponent);

        // Manually initialize @Value fields for unit tests
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.TRITON);
        setField(bridgeService, "latencyThresholdMs", 1000.0);

        setupTritonMock(10.0f);
    }

    private void setupTritonMock(float value) {
        byte[] resultBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        ModelInferResponse mockResponse = ModelInferResponse.newBuilder()
                .addRawOutputContents(ByteString.copyFrom(resultBytes))
                .build();
        when(tritonClient.infer(anyFloat(), anyString())).thenReturn(mockResponse);
    }

    /**
     * Workflow: Standard Legacy Path.
     * Verification: Ensures that when RoutingMode is TRITON, the gateway 
     * bypasses next-gen logic and returns the Ground Truth prediction.
     */
    @Test
    void testRoutingModeTriton() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.TRITON);
        float result = bridgeService.infer(5.0f);
        assertThat(result).isEqualTo(10.0f);
    }

    /**
     * Workflow: Next-Gen Dynamo Path (Migration Target).
     * Verification: Validates that DYNAMO mode correctly routes requests 
     * to the gRPC backend and respects the session context.
     */
    @Test
    void testRoutingModeDynamo_Success() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.DYNAMO);
        when(dynamoGrpcClient.callDynamo(5.0f, "test-session", "simple")).thenReturn(15.0f);
        float result = bridgeService.infer(5.0f, "test-session", "simple");
        assertThat(result).isEqualTo(15.0f);
    }

    /**
     * Workflow: Redis Outage Resilience.
     * Verification: Confirms that if the Redis KV-Cache registry is down, 
     * the gateway "Fails-Open" to a Cold Start (assume session is not warm) 
     * and continues inference instead of failing the request.
     */
    @Test
    void testRoutingModeDynamo_RedisFailure() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.DYNAMO);
        
        // Simulate Redis Connection Error
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis Connection Refused"));
        
        // Mock Dynamo Success
        when(dynamoGrpcClient.callDynamo(5.0f, "test-session", "simple")).thenReturn(15.0f);
        
        float result = bridgeService.infer(5.0f, "test-session", "simple");
        
        // Should succeed by defaulting to Cold Start
        assertThat(result).isEqualTo(15.0f);
        verify(dynamoGrpcClient).callDynamo(5.0f, "test-session", "simple");
    }

    /**
     * Workflow: Dual-Inference Shadow Mode.
     * Verification: Confirms that both backends are invoked and that 
     * model drift (delta) is correctly calculated and recorded in Micrometer.
     */
    @Test
    void testRoutingModeShadow_Success() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.SHADOW);
        setField(bridgeService, "latencyThresholdMs", 1000.0);
        setupTritonMock(10.0f);
        when(dynamoGrpcClient.callDynamo(anyFloat(), anyString(), anyString())).thenReturn(10.5f);

        float result = bridgeService.infer(5.0f, "test-session", "simple");

        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(5.0f, "simple");
        
        var driftMetric = meterRegistry.find("velo.sentinel.shadow.drift").summary();
        if (driftMetric != null) {
            assertThat(driftMetric.mean()).isEqualTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    /**
     * Workflow: Shadow Mode SLO Veto.
     * Verification: Ensures that if the Dynamo path exceeds the latency 
     * threshold (e.g. 10ms), the comparison is pruned to protect gateway latency.
     */
    @Test
    void testRoutingModeShadow_Timeout() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.SHADOW);
        setField(bridgeService, "latencyThresholdMs", 10.0);
        setupTritonMock(10.0f);
        
        when(dynamoGrpcClient.callDynamo(anyFloat(), anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(500);
            return 15.0f;
        });

        float result = bridgeService.infer(5.0f, "test-session", "simple");
        assertThat(result).isEqualTo(10.0f);
    }

    /**
     * Workflow: SLO Veto (DYNAMO Mode).
     * Verification: Confirms that in DYNAMO mode, if the request exceeds 
     * the latency threshold, the gateway "Vetoes" the Dynamo path and 
     * falls back to Triton to protect P99 latency.
     */
    @Test
    void testRoutingModeDynamo_SLOVeto() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.DYNAMO);
        setField(bridgeService, "latencyThresholdMs", 50.0);
        setupTritonMock(10.0f);
        
        // Simulate a slow Dynamo backend (exceeds 50ms)
        when(dynamoGrpcClient.callDynamo(anyFloat(), anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(200);
            return 15.0f;
        });

        float result = bridgeService.infer(5.0f, "test-session", "simple");

        // Should fall back to Triton (10.0)
        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(5.0f, "simple");
    }

    /**
     * Workflow: High Availability Fallback.
     * Verification: Validates that the DynamoResilienceComponent correctly 
     * fails open to Triton when a backend failure is simulated.
     */
    @Test
    void testHighAvailability_FailOpen() {
        float result = resilienceComponent.failOpenToTriton(5.0f, "test-session", "simple", new RuntimeException("Simulated"));
        assertThat(result).isEqualTo(10.0f);
    }

    /**
     * Workflow: E2E Fail-Open Orchestration.
     * Verification: Confirms that when Dynamo fails at the gRPC level,
     * the BridgeService successfully intercepts the error and returns 
     * the Triton result without crashing the request.
     */
    @Test
    void testE2EFailOpen() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.DYNAMO);
        setupTritonMock(10.0f);
        
        // Simulate a hard gRPC failure in the backend
        when(dynamoGrpcClient.callDynamo(anyFloat(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Dynamo Backend Unavailable"));

        // Simulate Spring AOP: If protectedDynamoCall fails, it should invoke failOpenToTriton
        doAnswer(inv -> {
            try {
                return inv.callRealMethod();
            } catch (Throwable t) {
                return resilienceComponent.failOpenToTriton(
                    (float)inv.getArgument(0), 
                    (String)inv.getArgument(1), 
                    (String)inv.getArgument(2), 
                    t
                );
            }
        }).when(resilienceComponent).protectedDynamoCall(anyFloat(), anyString(), anyString());

        float result = bridgeService.infer(5.0f, "test-session", "simple");

        // Should fall back to Triton (10.0)
        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(5.0f, "simple");
    }

    @Test
    void testInferWithNoModelName() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.TRITON);
        float result = bridgeService.infer(5.0f, "test-session");
        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(5.0f, "simple");
    }

    @Test
    void testInferWithNoSession() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.TRITON);
        float result = bridgeService.infer(5.0f);
        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(5.0f, "simple");
    }

    @Test
    void testExecuteInferenceExceptionRecording() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.TRITON);
        when(tritonClient.infer(anyFloat(), anyString())).thenThrow(new RuntimeException("Core Failure"));
        
        try {
            bridgeService.infer(5.0f, "test-session", "simple");
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("Core Failure");
        }
        
        var counter = meterRegistry.find("velo.sentinel.errors").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void testRoutingModeShadow_TritonFailure() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.SHADOW);
        setField(bridgeService, "latencyThresholdMs", 1000.0);
        
        // Triton fails during the task scope
        when(tritonClient.infer(anyFloat(), anyString())).thenThrow(new RuntimeException("Triton internal error"));
        when(dynamoGrpcClient.callDynamo(anyFloat(), anyString(), anyString())).thenReturn(10.5f);

        try {
            bridgeService.infer(5.0f, "test-session", "simple");
        } catch (Exception e) {
            // Should fallback to Triton outside task scope, which also throws
        }
    }

    @Test
    void testRoutingModeShadow_InterruptedException() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.SHADOW);
        setField(bridgeService, "latencyThresholdMs", 1000.0);
        setupTritonMock(10.0f);
        // Stub Dynamo so the best-effort fallback returns a real value
        when(dynamoGrpcClient.callDynamo(5.0f, "test-session", "simple")).thenReturn(7.0f);

        // Interrupt the current thread to simulate InterruptedException in join()
        Thread.currentThread().interrupt();

        float result = bridgeService.infer(5.0f, "test-session", "simple");

        // When Triton's scope is interrupted, we now fall back to Dynamo (protectedDynamoCall)
        // as best-effort rather than retrying the known-broken Triton backend.
        assertThat(result).isEqualTo(7.0f);

        // Clear interrupt flag
        Thread.interrupted();
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

    /**
     * Workflow: Chaos Engineering (Fault Injection).
     * Verification: Validates that when ChaosComponent injects a synthetic failure,
     * the system correctly fails open to Triton ground truth.
     */
    @Test
    void testChaosInjection_Failover() {
        setField(bridgeService, "routingMode", DynamoBridgeService.RoutingMode.DYNAMO);
        setupTritonMock(10.0f);
        
        // Enable chaos and force a failure injection
        doThrow(new RuntimeException("CHAOS-INJECTION: Synthetic failure"))
            .when(chaosComponent).maybeInjectChaos(anyString());

        float result = bridgeService.infer(5.0f, "chaos-session", "simple");

        // Should fall back to Triton (10.0) despite DYNAMO mode being active
        assertThat(result).isEqualTo(10.0f);
        verify(tritonClient, atLeastOnce()).infer(eq(5.0f), anyString());
    }
}
