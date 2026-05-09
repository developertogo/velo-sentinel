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

# Create __init__.py for the python package
touch $OUTPUT_DIR/python/sentinel_sdk/__init__.py

echo "🚀 Generating Node.js SDK..."
# For Node.js, we provide the descriptor set for dynamic loading
protoc \
    -I$PROTO_DIR \
    --include_imports \
    --include_source_info \
    --descriptor_set_out=$OUTPUT_DIR/node/sentinel_descriptor.pb \
    $PROTO_DIR/inference.proto $PROTO_DIR/model_config.proto $PROTO_DIR/grpc_service.proto

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
  "dependencies": {
    "@grpc/grpc-js": "^1.6.0",
    "@grpc/proto-loader": "^0.7.0"
  }
}
EOF

echo "✅ SDK Generation Complete!"
echo "Python SDK: $OUTPUT_DIR/python"
echo "Node.js SDK: $OUTPUT_DIR/node"
