package com.velo.sentinel.dev;

import com.velo.sentinel.grpc.DynamoInferenceRequest;
import com.velo.sentinel.grpc.DynamoInferenceResponse;
import com.velo.sentinel.grpc.DynamoServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * MOCK DYNAMO SERVER: Starts a real gRPC server on port 9001.
 * Only active when the "dev" profile is enabled.
 */
@Component
@Profile("mock-dynamo")
public class MockDynamoServer {
    private static final Logger log = LoggerFactory.getLogger(MockDynamoServer.class);
    private Server server;

    /**
     * Starts the mock gRPC server.
     * 
     * @throws IOException If the server fails to bind to the port.
     */
    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(9001)
            .addService(new DynamoServiceImpl())
            .build()
            .start();
        log.info("MOCK-DYNAMO-SERVER: Started successfully on port 9001 (Profile: mock-dynamo)");
    }

    /**
     * Shuts down the mock gRPC server.
     */
    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("MOCK-DYNAMO-SERVER: Shutting down...");
            server.shutdown();
        }
    }

    /**
     * Implementation of the Dynamo gRPC service.
     */
    static class DynamoServiceImpl extends DynamoServiceGrpc.DynamoServiceImplBase {
        @Override
        public void infer(DynamoInferenceRequest request, StreamObserver<DynamoInferenceResponse> responseObserver) {
            // Simulate normal network latency (25ms)
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            float prediction = request.getInputValue() + 0.5f;
            
            DynamoInferenceResponse response = DynamoInferenceResponse.newBuilder()
                .setPrediction(prediction)
                .setStatus("SUCCESS")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
