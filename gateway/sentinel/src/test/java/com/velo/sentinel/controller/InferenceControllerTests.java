package com.velo.sentinel.controller;

import com.velo.sentinel.model.InferenceRequest;
import com.velo.sentinel.model.InferenceResponse;
import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import com.velo.sentinel.service.DynamoBridgeService;
import com.velo.sentinel.streaming.ResilientStreamBridge;
import com.velo.sentinel.streaming.StreamEvent;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InferenceControllerTests {

    private final DynamoBridgeService bridgeService = mock(DynamoBridgeService.class);
    private final ResilientStreamBridge streamBridge = mock(ResilientStreamBridge.class);
    private final InferenceController controller = new InferenceController(bridgeService, streamBridge);

    @Test
    void testInferSuccess() {
        InferenceRequest request = new InferenceRequest("session-1", "llama-3", 10.0f, true, PriorityTier.REALTIME, 5, ModelPrecision.FP16);
        when(bridgeService.sentinelExecute(anyFloat(), anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
            .thenReturn(42.0f);

        ResponseEntity<InferenceResponse> response = controller.infer(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(42.0f, response.getBody().prediction());
    }

    @Test
    void testInferRateLimited() {
        InferenceRequest request = new InferenceRequest("session-2", "llama-3", 10.0f, true, PriorityTier.REALTIME, 5, ModelPrecision.FP16);
        when(bridgeService.sentinelExecute(anyFloat(), anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
            .thenThrow(mock(RequestNotPermitted.class));

        ResponseEntity<InferenceResponse> response = controller.infer(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(InferenceResponse.Status.SLA_VIOLATED, response.getBody().status());
    }

    @Test
    void testInferSystemOutage() {
        InferenceRequest request = new InferenceRequest("session-3", "llama-3", 10.0f, true, PriorityTier.REALTIME, 5, ModelPrecision.FP16);
        when(bridgeService.sentinelExecute(anyFloat(), anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("Total failure"));

        ResponseEntity<InferenceResponse> response = controller.infer(request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(InferenceResponse.Status.BACKEND_OUTAGE, response.getBody().status());
    }

    @Test
    void testStreamEndpoint() {
        @SuppressWarnings("unchecked")
        Flow.Publisher<StreamEvent> mockPublisher = mock(Flow.Publisher.class);
        when(streamBridge.executeResilientStream(anyFloat(), anyString(), anyString())).thenReturn(mockPublisher);

        SseEmitter emitter = controller.stream(1.0f, "session-stream", "model-stream");

        assertNotNull(emitter);
        verify(streamBridge).executeResilientStream(1.0f, "session-stream", "model-stream");
    }
}
