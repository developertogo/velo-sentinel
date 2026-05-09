import grpc
import time
import struct
from . import grpc_service_pb2
from . import grpc_service_pb2_grpc
from . import inference_pb2

class SentinelClient:
    def __init__(self, host="localhost", port=8080, use_tls=False):
        self.target = f"{host}:{port}"
        if use_tls:
            credentials = grpc.ssl_channel_credentials()
            self.channel = grpc.secure_channel(self.target, credentials)
        else:
            self.channel = grpc.insecure_channel(self.target)
        self.stub = grpc_service_pb2_grpc.GRPCInferenceServiceStub(self.channel)

    def infer(self, value, model_name="simple", session_id="python-client", timeout=1.0):
        contents = inference_pb2.InferTensorContents(fp32_contents=[value])
        input_tensor = grpc_service_pb2.ModelInferRequest.InferInputTensor(
            name="INPUT", datatype="FP32", shape=[1], contents=contents
        )
        request = grpc_service_pb2.ModelInferRequest(model_name=model_name, inputs=[input_tensor])
        try:
            start_time = time.time()
            response = self.stub.ModelInfer(request, timeout=timeout)
            duration = (time.time() - start_time) * 1000
            if response.raw_output_contents:
                result = struct.unpack('<f', response.raw_output_contents[0])[0]
                return {"result": result, "latency_ms": duration, "model": model_name}
            return None
        except grpc.RpcError as e:
            print(f"Inference failed: {e.code()} - {e.details()}")
            raise

    def close(self):
        self.channel.close()
    def __enter__(self): return self
    def __exit__(self, exc_type, exc_val, exc_tb): self.close()
