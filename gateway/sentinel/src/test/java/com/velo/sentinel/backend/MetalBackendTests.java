package com.velo.sentinel.backend;

import com.velo.sentinel.nativelib.VeloCoreBridge;
import com.velo.sentinel.nativelib.VeloNativeLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MetalBackendTests {

    private final VeloNativeLibrary nativeLib = mock(VeloNativeLibrary.class);
    private final VeloCoreBridge bridge = mock(VeloCoreBridge.class);
    private MetalBackend backend;

    @BeforeEach
    void setUp() {
        backend = new MetalBackend(nativeLib, "test-model");
    }

    @Test
    void testInferSimulationPath() {
        // bridge is null by default
        float input = 10.0f;
        float result = backend.infer(input, "session-1", "model-1");
        assertEquals(input * 1.05f, result, 0.001);
    }

    @Test
    void testInferNativePath() {
        ReflectionTestUtils.setField(backend, "bridge", bridge);
        when(bridge.generate(anyList(), anyInt())).thenReturn(List.of(42));

        float input = 10.0f;
        float result = backend.infer(input, "session-2", "model-1");

        assertEquals(42.0f, result);
        verify(bridge).generate(anyList(), eq(1));
    }

    @Test
    void testInferNativePathEmptyResult() {
        ReflectionTestUtils.setField(backend, "bridge", bridge);
        when(bridge.generate(anyList(), anyInt())).thenReturn(List.of());

        float input = 10.0f;
        float result = backend.infer(input, "session-3", "model-1");

        assertEquals(input, result);
    }

    @Test
    void testClose() {
        ReflectionTestUtils.setField(backend, "bridge", bridge);
        backend.close();
        verify(bridge).close();
    }
}
