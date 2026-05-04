package com.velo.sentinel.client;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.ModelInferRequest;
import com.velo.sentinel.grpc.ModelInferResponse;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class TritonGrpcClientTests {

    private TritonGrpcClient client;
    private ManagedChannel mockChannel;
    private GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub mockStub;

    @BeforeEach
    void setup() {
        client = new TritonGrpcClient();
        ReflectionTestUtils.setField(client, "modelName", "simple");

        mockChannel = mock(ManagedChannel.class);
        mockStub = mock(GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub.class);

        // Mock the withDeadlineAfter builder pattern
        when(mockStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(mockStub);

        ReflectionTestUtils.setField(client, "channel", mockChannel);
        ReflectionTestUtils.setField(client, "stub", mockStub);
    }

    @Test
    void testInferSuccess() {
        ModelInferResponse mockResponse = ModelInferResponse.newBuilder().build();
        when(mockStub.modelInfer(any(ModelInferRequest.class))).thenReturn(mockResponse);

        ModelInferResponse response = client.infer(5.0f);
        assertThat(response).isNotNull();
    }

    @Test
    void testInferException() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Backend down"));
        when(mockStub.modelInfer(any(ModelInferRequest.class))).thenThrow(exception);

        assertThrows(StatusRuntimeException.class, () -> client.infer(5.0f));
    }

    @Test
    void testShutdownSuccess() throws InterruptedException {
        when(mockChannel.shutdown()).thenReturn(mockChannel);
        when(mockChannel.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
        client.shutdown();
        verify(mockChannel).shutdown();
    }

    @Test
    void testShutdownInterrupted() throws InterruptedException {
        when(mockChannel.shutdown()).thenReturn(mockChannel);
        when(mockChannel.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        
        client.shutdown();
        
        verify(mockChannel).shutdownNow();
    }
}
