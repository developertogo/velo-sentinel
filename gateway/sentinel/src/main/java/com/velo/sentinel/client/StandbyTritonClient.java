package com.velo.sentinel.client;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import com.velo.sentinel.grpc.InferTensorContents;
import com.velo.sentinel.grpc.ModelInferRequest;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.grpc.ServerLiveRequest;
import com.velo.sentinel.grpc.ServerLiveResponse;

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
 * StandbyTritonClient: The Disaster Recovery Interface.
 * Connects to a secondary cloud region when the primary is unavailable.
 */
@Service
public class StandbyTritonClient {

    private static final Logger log = LoggerFactory.getLogger(StandbyTritonClient.class);

    private ManagedChannel channel;
    private GRPCInferenceServiceBlockingStub stub;

    @Value("${triton.standby.host:secondary.cloud.provider}")
    private String host;

    @Value("${triton.standby.port:8001}")
    private int port;

    @Value("${triton.grpc.model-name:simple}")
    private String modelName;

    /**
     * Post-construct initialization to establish cross-cloud connectivity.
     */
    @PostConstruct
    public void init() {
        String target = "dns:///" + host + ":" + port;
        log.info("STANDBY-CLIENT: Initializing cross-cloud connection to: {} (Model: {})", target, modelName);
        
        this.channel = ManagedChannelBuilder
                .forTarget(target)
                .defaultLoadBalancingPolicy("round_robin")
                .usePlaintext()
                .build();

        this.stub = GRPCInferenceServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Executes an inference call to the failover region.
     * 
     * @param value The input float value.
     * @param modelNameOverride The model name to target in the standby region.
     * @return The raw ModelInferResponse from the standby Triton server.
     */
    public ModelInferResponse infer(float value, String modelNameOverride) {
        try {
            log.warn("STANDBY-EXECUTION: Routing request to secondary cloud provider.");
            
            InferTensorContents contents = InferTensorContents.newBuilder()
                    .addFp32Contents(value)
                    .build();

            ModelInferRequest.InferInputTensor input = ModelInferRequest.InferInputTensor.newBuilder()
                    .setName("INPUT")
                    .setDatatype("FP32")
                    .addShape(1L)
                    .setContents(contents)
                    .build();

            ModelInferRequest request = ModelInferRequest.newBuilder()
                    .setModelName(modelNameOverride)
                    .addInputs(input)
                    .build();

            return stub.withDeadlineAfter(500, TimeUnit.MILLISECONDS).modelInfer(request);
            
        } catch (StatusRuntimeException e) {
            log.error("STANDBY-CLIENT-ERROR: Failover region unreachable. Status: {}", e.getStatus().getCode());
            throw e;
        }
    }

    /**
     * Health check for the standby region.
     * 
     * @return {@code true} if the standby region is live, {@code false} otherwise.
     */
    public boolean checkHealth() {
        try {
            ServerLiveResponse response = stub.withDeadlineAfter(200, TimeUnit.MILLISECONDS)
                    .serverLive(ServerLiveRequest.getDefaultInstance());
            return response.getLive();
        } catch (Exception e) {
            log.warn("STANDBY-HEALTH-CHECK: Standby region unreachable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pre-destroy hook to release standby network resources.
     */
    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            log.info("STANDBY-CLIENT: Shutting down standby channel...");
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}
