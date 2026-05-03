package com.velo.sentinel.dev;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.ModelInferRequest;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.google.protobuf.ByteString;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MOCK TRITON SERVER: Starts a real gRPC server on port 8001.
 * Only active when the "dev" profile is enabled.
 * Acts as the 'Ground Truth' for simulation.
 */
@Component
@Profile("mock-triton")
public class MockTritonServer {
    private static final Logger log = LoggerFactory.getLogger(MockTritonServer.class);
    private Server server;

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(8001)
            .addService(new TritonServiceImpl())
            .build()
            .start();
        log.info("MOCK-TRITON-SERVER: Started successfully on port 8001 (Profile: mock-triton)");
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("MOCK-TRITON-SERVER: Shutting down...");
            server.shutdown();
        }
    }

    static class TritonServiceImpl extends GRPCInferenceServiceGrpc.GRPCInferenceServiceImplBase {
        @Override
        public void modelInfer(ModelInferRequest request, StreamObserver<ModelInferResponse> responseObserver) {
            // Triton returns exactly the input value (Identity model)
            // This allows us to see the +0.5 drift from Dynamo
            float inputValue = request.getInputs(0).getContents().getFp32Contents(0);
            float tritonResult = inputValue * 2.0f;
            
            byte[] resultBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(tritonResult)
                .array();

            ModelInferResponse response = ModelInferResponse.newBuilder()
                .setModelName(request.getModelName())
                .addRawOutputContents(ByteString.copyFrom(resultBytes))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
