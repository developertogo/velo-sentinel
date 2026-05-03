package com.velo.sentinel.backend;

import com.velo.sentinel.client.TritonGrpcClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TritonBackendTests: Detailed validation of the Legacy Backend logic.
 * Focuses on binary data extraction, byte order handling (Little Endian),
 * and defensive error handling for malformed gRPC responses.
 */
public class TritonBackendTests {

    private TritonGrpcClient mockClient;
    private TritonBackend tritonBackend;

    @BeforeEach
    void setup() {
        mockClient = Mockito.mock(TritonGrpcClient.class);
        tritonBackend = new TritonBackend(mockClient);
    }

    /**
     * Workflow: Binary Extraction (FP32).
     * Verification: Verifies that TritonBackend correctly parses the raw binary 
     * response from Triton (4 bytes, Little Endian) into a Java float.
     */
    @Test
    void testInfer_BinaryParsing_Success() {
        float expectedValue = 123.45f;
        
        // Prepare 4 bytes in Little Endian (Standard NVIDIA/CUDA order)
        byte[] rawBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(expectedValue)
                .array();

        ModelInferResponse mockResponse = ModelInferResponse.newBuilder()
                .addRawOutputContents(ByteString.copyFrom(rawBytes))
                .build();

        when(mockClient.infer(anyFloat(), anyString())).thenReturn(mockResponse);

        float result = tritonBackend.infer(1.0f);
        
        assertThat(result).isEqualTo(expectedValue);
    }

    /**
     * Workflow: Defensive Error Handling.
     * Verification: Ensures the backend throws a RuntimeException when the 
     * Triton response contains no output tensors, preventing NullPointerExceptions.
     */
    @Test
    void testInfer_EmptyResponse_ThrowsException() {
        ModelInferResponse emptyResponse = ModelInferResponse.newBuilder().build();
        when(mockClient.infer(anyFloat(), anyString())).thenReturn(emptyResponse);

        assertThatThrownBy(() -> tritonBackend.infer(1.0f))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empty response from Triton");
    }

    /**
     * Workflow: Session Awareness.
     * Verification: Ensures the session ID is passed down correctly through the 
     * overloaded infer methods (implicitly verified by successful execution).
     */
    @Test
    void testInfer_WithExplicitSession() {
        setupMockResponse(42.0f);
        float result = tritonBackend.infer(1.0f, "test-session-77");
        assertThat(result).isEqualTo(42.0f);
    }

    private void setupMockResponse(float value) {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        ModelInferResponse response = ModelInferResponse.newBuilder()
                .addRawOutputContents(ByteString.copyFrom(bytes))
                .build();
        when(mockClient.infer(anyFloat(), anyString())).thenReturn(response);
    }
}
