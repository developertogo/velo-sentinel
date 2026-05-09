#!/bin/bash
# sdk-gen.sh: Multi-Language SDK Generator for Velo-Sentinel
# Generates high-performance Python and Node.js clients from gRPC descriptors.

set -e

PROTO_DIR="gateway/sentinel/src/main/proto"
OUTPUT_DIR="sdks"

mkdir -p $OUTPUT_DIR/python/sentinel_sdk
mkdir -p $OUTPUT_DIR/node

echo "🚀 Generating Python SDK..."
# Generate both protobuf messages and gRPC service code
python3 -m grpc_tools.protoc \
    -I$PROTO_DIR \
    --python_out=$OUTPUT_DIR/python/sentinel_sdk \
    --grpc_python_out=$OUTPUT_DIR/python/sentinel_sdk \
    $PROTO_DIR/inference.proto $PROTO_DIR/model_config.proto $PROTO_DIR/grpc_service.proto

# Fix relative imports in generated python files
sed -i '' 's/import inference_pb2/from . import inference_pb2/g' $OUTPUT_DIR/python/sentinel_sdk/*.py
sed -i '' 's/import model_config_pb2/from . import model_config_pb2/g' $OUTPUT_DIR/python/sentinel_sdk/*.py
sed -i '' 's/import grpc_service_pb2/from . import grpc_service_pb2/g' $OUTPUT_DIR/python/sentinel_sdk/*.py

# Create __init__.py for the python package
touch $OUTPUT_DIR/python/sentinel_sdk/__init__.py

# Add the high-level Python client
cat > $OUTPUT_DIR/python/sentinel_sdk/client.py <<EOF
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
EOF

# Add Python Example
cat > $OUTPUT_DIR/python/example.py <<EOF
from sentinel_sdk.client import SentinelClient
def main():
    with SentinelClient(host="localhost", port=8080) as client:
        for val in [42.0, 100.5]:
            response = client.infer(val, model_name="phi-2-metal")
            if response: print(f"SUCCESS: Result: {response['result']:.4f} | Latency: {response['latency_ms']:.2f}ms")
if __name__ == "__main__":
    main()
EOF

echo "🚀 Generating Node.js SDK..."
# For Node.js, we provide the descriptor set for dynamic loading
protoc \
    -I$PROTO_DIR \
    --include_imports \
    --include_source_info \
    --descriptor_set_out=$OUTPUT_DIR/node/sentinel_descriptor.pb \
    $PROTO_DIR/inference.proto $PROTO_DIR/model_config.proto $PROTO_DIR/grpc_service.proto

# Add Node.js Example
cat > $OUTPUT_DIR/node/example.js <<EOF
const { SentinelClient } = require('./index');
async function run() {
    const client = new SentinelClient('localhost', 8080);
    try {
        const response = await client.infer(42.0, 'phi-2-metal');
        if (response) console.log(\`SUCCESS: Result: \${response.result} | Latency: \${response.latency_ms}ms\`);
    } finally {
        client.close();
    }
}
run();
EOF

echo "📦 Packaging SDKs..."

# Python setup.py
cat > $OUTPUT_DIR/python/setup.py <<EOF
from setuptools import setup, find_packages

setup(
    name="velo-sentinel-sdk",
    version="1.0.0",
    packages=find_packages(),
    install_requires=[
        "grpcio>=1.48.0",
        "protobuf>=3.19.0",
    ],
    description="High-performance client for Velo-Sentinel Inference Gateway",
)
EOF

# Node.js package.json
cat > $OUTPUT_DIR/node/package.json <<EOF
{
  "name": "velo-sentinel-sdk",
  "version": "1.0.0",
  "description": "High-performance client for Velo-Sentinel Inference Gateway",
  "main": "index.js",
  "scripts": {
    "test": "jest"
  },
  "dependencies": {
    "@grpc/grpc-js": "^1.6.0",
    "@grpc/proto-loader": "^0.7.0"
  },
  "devDependencies": {
    "jest": "^29.0.0"
  }
}
EOF

echo "✅ SDK Generation Complete!"
echo "Python SDK: $OUTPUT_DIR/python"
echo "Node.js SDK: $OUTPUT_DIR/node"
