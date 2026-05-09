package com.velo.sentinel.client;

import com.velo.sentinel.grpc.DynamoInferenceRequest;
import com.velo.sentinel.grpc.DynamoInferenceResponse;
import com.velo.sentinel.grpc.DynamoServiceGrpc;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DynamoGrpcClientTests {

    private DynamoGrpcClient client;
    private ManagedChannel mockChannel;
    private DynamoServiceGrpc.DynamoServiceBlockingStub mockStub;

    @BeforeEach
    void setup() {
        client = new DynamoGrpcClient();
        
        mockChannel = mock(ManagedChannel.class);
        mockStub = mock(DynamoServiceGrpc.DynamoServiceBlockingStub.class);

        ReflectionTestUtils.setField(client, "channel", mockChannel);
        ReflectionTestUtils.setField(client, "blockingStub", mockStub);
    }

    @Test
    void testCallDynamo() {
        DynamoInferenceResponse mockResponse = DynamoInferenceResponse.newBuilder().setPrediction(10.5f).build();
        when(mockStub.infer(any(DynamoInferenceRequest.class))).thenReturn(mockResponse);

        float result = client.callDynamo(5.0f, "test-session", "simple");
        assertThat(result).isEqualTo(10.5f);
    }

    @Test
    void testShutdown() {
        client.shutdown();
        verify(mockChannel).shutdown();
    }

    @Test
    void testShutdownNull() {
        ReflectionTestUtils.setField(client, "channel", null);
        client.shutdown(); // Should not throw
    }

    @Test
    void testCheckHealth() {
        when(mockChannel.getState(true)).thenReturn(io.grpc.ConnectivityState.READY);
        assertThat(client.checkHealth()).isTrue();

        when(mockChannel.getState(true)).thenReturn(io.grpc.ConnectivityState.TRANSIENT_FAILURE);
        assertThat(client.checkHealth()).isFalse();

        ReflectionTestUtils.setField(client, "channel", null);
        assertThat(client.checkHealth()).isFalse();
    }
}
