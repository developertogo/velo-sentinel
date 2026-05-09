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
