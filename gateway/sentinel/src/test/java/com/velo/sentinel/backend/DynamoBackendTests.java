package com.velo.sentinel.backend;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.context.InferenceContext;
import com.velo.sentinel.service.KVCacheRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.ScopedValue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DynamoBackendTests: Validating the Next-Gen Disaggregated Engine.
 */
public class DynamoBackendTests {

    private DynamoGrpcClient mockClient;
    private KVCacheRegistry mockCacheRegistry;
    private DynamoBackend dynamoBackend;

    @BeforeEach
    void setup() {
        mockClient = Mockito.mock(DynamoGrpcClient.class);
        mockCacheRegistry = Mockito.mock(KVCacheRegistry.class);
        dynamoBackend = new DynamoBackend(mockClient, mockCacheRegistry);
    }

    /**
     * Workflow: Session State Lifecycle (COLD to WARM).
     */
    @Test
    void testSessionRegistry_WarmUpFlow() {
        String sessionId = "user-99";
        when(mockClient.callDynamo(anyFloat(), anyString(), anyString())).thenReturn(5.0f);
        
        // Simulate initial COLD state
        when(mockCacheRegistry.isSessionWarm(sessionId)).thenReturn(false);

        dynamoBackend.infer(1.0f, sessionId);
        
        // Verify it marked session as active on the internal backend node
        verify(mockCacheRegistry).markSessionActive(eq(sessionId), anyString());

        // Simulate subsequent WARM state
        when(mockCacheRegistry.isSessionWarm(sessionId)).thenReturn(true);
        dynamoBackend.infer(1.0f, sessionId);

        verify(mockClient, times(2)).callDynamo(1.0f, sessionId, "simple");
    }

    @Test
    void testInfer_ContextAwareness() {
        String testSession = "context-session";
        when(mockClient.callDynamo(anyFloat(), eq(testSession), anyString())).thenReturn(7.0f);

        float result = ScopedValue.where(InferenceContext.SESSION_ID, testSession)
                .call(() -> dynamoBackend.infer(1.0f));

        assertThat(result).isEqualTo(7.0f);
    }

    @Test
    void testInfer_DefaultSessionFallback() {
        when(mockClient.callDynamo(anyFloat(), eq("default-session"), anyString())).thenReturn(9.0f);

        float result = dynamoBackend.infer(1.0f);

        assertThat(result).isEqualTo(9.0f);
    }

    @Test
    void testInfer_MultiModelRouting() {
        String model = "llama-3";
        when(mockClient.callDynamo(anyFloat(), anyString(), eq(model))).thenReturn(10.0f);

        float result = dynamoBackend.infer(1.0f, "user-1", model);

        assertThat(result).isEqualTo(10.0f);
        verify(mockClient).callDynamo(1.0f, "user-1", model);
    }
}
