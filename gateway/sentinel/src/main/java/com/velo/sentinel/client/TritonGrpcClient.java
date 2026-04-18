package com.velo.sentinel.client;

import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc;
import com.velo.sentinel.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import com.velo.sentinel.grpc.InferTensorContents;
import com.velo.sentinel.grpc.ModelInferRequest;
import com.velo.sentinel.grpc.ModelInferResponse;
//import com.velo.sentinel.grpc.ModelInferRequest.InferInputTensor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TritonGrpcClient {

  private final ManagedChannel channel;
  private final GRPCInferenceServiceBlockingStub stub;

  public TritonGrpcClient() {
    this.channel = ManagedChannelBuilder
        .forAddress("localhost", 8001)
        .usePlaintext()
        .build();

    this.stub = GRPCInferenceServiceGrpc.newBlockingStub(channel);
  }

  public ModelInferResponse infer(float value) {

    // 2. FIX: Call InferTensorContents directly, not through ModelInferRequest
    InferTensorContents contents = InferTensorContents.newBuilder()
        .addFp32Contents(value)
        .build();

    // InferInputTensor IS nested, so this stays the same
    ModelInferRequest.InferInputTensor input = ModelInferRequest.InferInputTensor.newBuilder()
        .setName("INPUT")
        .setDatatype("FP32")
        .addShape(1L)
        .setContents(contents)
        .build();

    ModelInferRequest request = ModelInferRequest.newBuilder()
        .setModelName("simple")
        .addInputs(input)
        .build();

    return stub.modelInfer(request);
  }

  @PreDestroy
  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }
}