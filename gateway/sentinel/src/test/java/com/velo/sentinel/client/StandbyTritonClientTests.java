package com.velo.sentinel.client;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.grpc.ServerLiveResponse;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class StandbyTritonClientTests {

    @Mock
    private ManagedChannel channel;

    @Mock
    private GRPCInferenceServiceBlockingStub stub;

    @InjectMocks
    private StandbyTritonClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(client, "stub", stub);
    }

    @Test
    void testInferSuccess() {
        ModelInferResponse mockResponse = ModelInferResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.modelInfer(any())).thenReturn(mockResponse);

        ModelInferResponse response = client.infer(1.0f, "test-model");

        assertNotNull(response);
        verify(stub).modelInfer(any());
    }

    @Test
    void testInferFailure() {
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.modelInfer(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(StatusRuntimeException.class, () -> client.infer(1.0f, "test-model"));
    }

    @Test
    void testCheckHealthSuccess() {
        ServerLiveResponse mockResponse = ServerLiveResponse.newBuilder().setLive(true).build();
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.serverLive(any())).thenReturn(mockResponse);

        assertTrue(client.checkHealth());
    }

    @Test
    void testCheckHealthFailure() {
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.serverLive(any())).thenThrow(new RuntimeException("Server down"));

        assertFalse(client.checkHealth());
    }

    @Test
    void testShutdown() throws InterruptedException {
        when(channel.shutdown()).thenReturn(channel);
        when(channel.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        client.shutdown();
        
        verify(channel).shutdown();
    }
}
