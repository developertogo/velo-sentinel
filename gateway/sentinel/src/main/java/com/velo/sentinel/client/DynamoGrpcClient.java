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

@Component
public class DynamoGrpcClient {
    private DynamoServiceGrpc.DynamoServiceBlockingStub blockingStub;
    private ManagedChannel channel;

    @Value("${dynamo.grpc.host:localhost}")
    private String host;

    @Value("${dynamo.grpc.port:9001}")
    private int port;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = DynamoServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public float callDynamo(float value, String sessionId) {
        DynamoInferenceRequest request = DynamoInferenceRequest.newBuilder()
                .setInputValue(value)
                .setSessionId(sessionId)
                .build();

        DynamoInferenceResponse response = blockingStub.infer(request);
        return response.getPrediction();
    }
}
