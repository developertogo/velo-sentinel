package com.velo.sentinel.sdk;

import com.velo.sentinel.sdk.model.InferenceRequest;
import com.velo.sentinel.sdk.model.InferenceResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class ResilientSentinelClientTest {

    @Test
    void testBasicInference() throws Exception {
        ResilientSentinelClient client = new ResilientSentinelClient("http://localhost:8080", 0, 1);
        InferenceRequest request = new InferenceRequest("test-session", "test-model", 10.0f, false);
        
        InferenceResponse response = client.infer(request);
        
        assertNotNull(response);
        assertEquals("test-session", response.sessionId());
        assertEquals(InferenceResponse.Status.SUCCESS, response.status());
        client.shutdown();
    }

    @Test
    void testHedgingSuccess() throws Exception {
        // Set hedging delay very low (1ms) to ensure hedging is triggered
        ResilientSentinelClient client = new ResilientSentinelClient("http://localhost:8080", 1, 1);
        InferenceRequest request = new InferenceRequest("hedge-session", "test-model", 10.0f, false);
        
        InferenceResponse response = client.infer(request);
        
        assertNotNull(response);
        assertEquals(InferenceResponse.Status.SUCCESS, response.status());
        client.shutdown();
    }

    @Test
    void testAsyncInference() throws Exception {
        ResilientSentinelClient client = new ResilientSentinelClient("http://localhost:8080", 0, 1);
        InferenceRequest request = new InferenceRequest("test-session", "test-model", 10.0f, false);
        
        CompletableFuture<InferenceResponse> future = client.inferAsync(request);
        InferenceResponse response = future.get();
        
        assertNotNull(response);
        assertEquals(InferenceResponse.Status.SUCCESS, response.status());
        client.shutdown();
    }
}
