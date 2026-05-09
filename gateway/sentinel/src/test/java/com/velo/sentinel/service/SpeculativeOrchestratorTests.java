package com.velo.sentinel.service;

import com.velo.sentinel.backend.InferenceBackend;
import com.velo.sentinel.backend.MetalBackend;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SpeculativeOrchestratorTests {

    private final MetalBackend drafter = mock(MetalBackend.class);
    private final InferenceBackend target = mock(InferenceBackend.class);
    private final SpeculativeOrchestrator orchestrator = new SpeculativeOrchestrator(drafter);

    @Test
    void testSpeculativeSuccess() {
        when(drafter.infer(anyFloat(), anyString(), anyString())).thenReturn(5.0f);
        when(target.infer(eq(5.0f), anyString(), anyString())).thenReturn(10.0f);

        float result = orchestrator.executeSpeculative(1.0f, "session-1", "llama-target", target);

        assertEquals(10.0f, result);
        verify(drafter).infer(1.0f, "session-1", "drafter-m3");
        verify(target).infer(5.0f, "session-1", "llama-target");
    }

    @Test
    void testSpeculativeFallbackOnDrafterError() {
        when(drafter.infer(anyFloat(), anyString(), anyString())).thenThrow(new RuntimeException("Drafter fail"));
        when(target.infer(eq(1.0f), anyString(), anyString())).thenReturn(10.0f);

        float result = orchestrator.executeSpeculative(1.0f, "session-2", "llama-target", target);

        assertEquals(10.0f, result);
        verify(target).infer(1.0f, "session-2", "llama-target");
    }

    @Test
    void testSpeculativeFallbackOnDrafterTimeout() throws Exception {
        when(drafter.infer(anyFloat(), anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(100); // Exceed 30ms timeout
            return 5.0f;
        });
        when(target.infer(eq(1.0f), anyString(), anyString())).thenReturn(10.0f);

        float result = orchestrator.executeSpeculative(1.0f, "session-3", "llama-target", target);

        assertEquals(10.0f, result);
        verify(target).infer(1.0f, "session-3", "llama-target");
    }
}
