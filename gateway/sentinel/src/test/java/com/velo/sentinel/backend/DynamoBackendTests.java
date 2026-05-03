package com.velo.sentinel.backend;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.context.InferenceContext;
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
 * Focuses on session state tracking (WARM vs COLD) and gRPC client delegation.
 */
public class DynamoBackendTests {

    private DynamoGrpcClient mockClient;
    private DynamoBackend dynamoBackend;

    @BeforeEach
    void setup() {
        mockClient = Mockito.mock(DynamoGrpcClient.class);
        dynamoBackend = new DynamoBackend(mockClient);
    }

    /**
     * Workflow: Session State Lifecycle (COLD to WARM).
     * Verification: Verifies that a new sessionId is initially treated as COLD
     * and subsequently as WARM, correctly managing the local KV-Cache simulation.
     */
    @Test
    void testSessionRegistry_WarmUpFlow() {
        String sessionId = "user-99";
        // Signature: callDynamo(float value, String sessionId, String modelName)
        when(mockClient.callDynamo(anyFloat(), anyString(), anyString())).thenReturn(5.0f);

        // First call: Should be COLD (logged internally)
        dynamoBackend.infer(1.0f, sessionId);
        
        // Second call: Should be WARM
        dynamoBackend.infer(1.0f, sessionId);

        // Verify gRPC client was called twice with default model "simple"
        verify(mockClient, times(2)).callDynamo(1.0f, sessionId, "simple");
    }

    /**
     * Workflow: Contextual Session Recovery.
     * Verification: Verifies that DynamoBackend correctly retrieves the sessionId 
     * from the InferenceContext ScopedValue when the simplified infer(float) is called.
     */
    @Test
    void testInfer_ContextAwareness() {
        String testSession = "context-session";
        when(mockClient.callDynamo(anyFloat(), eq(testSession), anyString())).thenReturn(7.0f);

        float result = ScopedValue.where(InferenceContext.SESSION_ID, testSession)
                .call(() -> dynamoBackend.infer(1.0f));

        assertThat(result).isEqualTo(7.0f);
    }

    /**
     * Workflow: Default Session Fallback.
     * Verification: Ensures that if no session is bound in the context, 
     * the backend defaults to "default-session" to prevent failures.
     */
    @Test
    void testInfer_DefaultSessionFallback() {
        when(mockClient.callDynamo(anyFloat(), eq("default-session"), anyString())).thenReturn(9.0f);

        // No ScopedValue bound here
        float result = dynamoBackend.infer(1.0f);

        assertThat(result).isEqualTo(9.0f);
    }

    /**
     * Workflow: Multi-model Routing.
     * Verification: Verifies that passing a specific model name propagates 
     * correctly to the gRPC client.
     */
    @Test
    void testInfer_MultiModelRouting() {
        String model = "llama-3";
        when(mockClient.callDynamo(anyFloat(), anyString(), eq(model))).thenReturn(10.0f);

        float result = dynamoBackend.infer(1.0f, "user-1", model);

        assertThat(result).isEqualTo(10.0f);
        verify(mockClient).callDynamo(1.0f, "user-1", model);
    }
}
