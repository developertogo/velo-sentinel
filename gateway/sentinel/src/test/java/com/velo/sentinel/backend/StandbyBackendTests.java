package com.velo.sentinel.backend;

import com.velo.sentinel.client.StandbyTritonClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StandbyBackendTests {

    private final StandbyTritonClient standbyClient = Mockito.mock(StandbyTritonClient.class);
    private final StandbyBackend backend = new StandbyBackend(standbyClient);

    @Test
    void testInferSuccess() {
        float expectedValue = 42.0f;
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat(expectedValue);
        
        ModelInferResponse response = ModelInferResponse.newBuilder()
                .addRawOutputContents(ByteString.copyFrom(bytes))
                .build();
        
        when(standbyClient.infer(anyFloat(), anyString())).thenReturn(response);

        float result = backend.infer(1.0f, "session-1", "model-1");

        assertEquals(expectedValue, result);
    }

    @Test
    void testInferEmptyResponse() {
        ModelInferResponse response = ModelInferResponse.newBuilder().build();
        when(standbyClient.infer(anyFloat(), anyString())).thenReturn(response);

        assertThrows(RuntimeException.class, () -> backend.infer(1.0f, "session-1", "model-1"));
    }
}
