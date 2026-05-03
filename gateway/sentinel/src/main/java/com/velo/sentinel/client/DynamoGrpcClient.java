package com.velo.sentinel.client;

import com.velo.sentinel.grpc.DynamoInferenceRequest;
import com.velo.sentinel.grpc.DynamoInferenceResponse;
import com.velo.sentinel.grpc.DynamoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * DynamoGrpcClient: The Hardened Next-Gen Interface.
 * 
 * Manages the high-performance gRPC channel to the Dynamo inference service.
 * Implements standard Spring lifecycle management for clean startup and shutdown
 * of network resources.
 */
@Component
public class DynamoGrpcClient {
    private DynamoServiceGrpc.DynamoServiceBlockingStub blockingStub;
    private ManagedChannel channel;

    @Value("${dynamo.grpc.host:localhost}")
    private String host;

    @Value("${dynamo.grpc.port:9001}")
    private int port;

    /**
     * Initializes the gRPC channel and stubs at application startup.
     * Uses plaintext communication for the internal "Inference Lab" environment.
     */
    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = DynamoServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Ensures clean resource teardown by shutting down the gRPC channel 
     * during application termination.
     */
    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    /**
     * Executes the gRPC call to the Dynamo service for a specific model.
     * Translates domain parameters into Proto-generated stubs.
     * 
     * @param value Input feature value.
     * @param sessionId Session ID for remote cache affinity.
     * @param modelName Target model for inference.
     * @return Prediction result extracted from the gRPC response.
     */
    public float callDynamo(float value, String sessionId, String modelName) {
        DynamoInferenceRequest request = DynamoInferenceRequest.newBuilder()
                .setInputValue(value)
                .setSessionId(sessionId)
                .setModelName(modelName)
                .build();

        DynamoInferenceResponse response = blockingStub.infer(request);
        return response.getPrediction();
    }
}
