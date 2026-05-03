package com.velo.sentinel.client;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import com.velo.sentinel.grpc.InferTensorContents;
import com.velo.sentinel.grpc.ModelInferRequest;
import com.velo.sentinel.grpc.ModelInferResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * TritonGrpcClient: The Hardened Legacy Interface.
 * Handles communication with NVIDIA Triton using the standard gRPC inference protocol.
 */
@Service
public class TritonGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(TritonGrpcClient.class);

    private ManagedChannel channel;
    private GRPCInferenceServiceBlockingStub stub;

    @Value("${triton.grpc.host:localhost}")
    private String host;

    @Value("${triton.grpc.port:8001}")
    private int port;

    @Value("${triton.grpc.model-name:simple}")
    private String modelName;

    @PostConstruct
    public void init() {
        log.info("TRITON-CLIENT: Connecting to legacy backend at {}:{} (Model: {})", host, port, modelName);
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        this.stub = GRPCInferenceServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Executes an inference call to the legacy Triton backend using the configured default model.
     */
    public ModelInferResponse infer(float value) {
        return infer(value, modelName);
    }

    /**
     * Executes an inference call to the legacy Triton backend for a specific model.
     * Uses the standard ModelInferRequest pattern required by NVIDIA.
     */
    public ModelInferResponse infer(float value, String modelNameOverride) {
        try {
            // 1. Prepare the tensor contents (FP32)
            InferTensorContents contents = InferTensorContents.newBuilder()
                    .addFp32Contents(value)
                    .build();

            // 2. Prepare the input tensor definition
            ModelInferRequest.InferInputTensor input = ModelInferRequest.InferInputTensor.newBuilder()
                    .setName("INPUT")
                    .setDatatype("FP32")
                    .addShape(1L) // Batch size 1
                    .setContents(contents)
                    .build();

            // 3. Assemble the full request
            ModelInferRequest request = ModelInferRequest.newBuilder()
                    .setModelName(modelNameOverride)
                    .addInputs(input)
                    .build();

            // 4. Execute with a timeout to ensure SLO compliance
            return stub.withDeadlineAfter(200, TimeUnit.MILLISECONDS).modelInfer(request);
            
        } catch (StatusRuntimeException e) {
            log.error("TRITON-CLIENT-ERROR: gRPC call failed for model {}. Status: {}, Description: {}", 
                    modelNameOverride, e.getStatus().getCode(), e.getStatus().getDescription());
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            log.info("TRITON-CLIENT: Shutting down channel...");
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}