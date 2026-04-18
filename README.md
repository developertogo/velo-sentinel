# Velo Sentinel

> High-performance Java 25 Virtual Thread-based inference gateway for NVIDIA Triton with SLA-aware request shaping and adaptive batching.

## Mission
To provide a production-grade, high-concurrency ML model serving gateway that abstracts backend complexity while ensuring strict SLA compliance through intelligent request orchestration.

## Key Objectives
- **Production-Style Serving**: A robust inference gateway built on **Spring Boot 4** and **Java 25**, bridging the gap between client applications and **NVIDIA Triton Inference Server**.
- **Orchestration Layer**: A sophisticated model orchestration layer that enables scalable routing and multi-model serving architectures.
- **High-Concurrency Execution**: Leveraging **Java Virtual Threads** (Project Loom) to achieve low-latency and high-throughput request processing, even under heavy distributed loads.
- **Type-Safe Contract System**: A **protobuf-based gRPC contract system** ensuring strict schema enforcement and automated stub generation for reliable cross-service communication.
- **Extensible Architecture**: A clean service boundary design that separates the API Gateway, Inference Orchestration, and Model Execution layers.

## Technology Stack
- **Language**: Java 25 (Optimized for Virtual Threads)
- **Framework**: Spring Boot 4.0.5
- **Communication**: gRPC (Primary) & HTTP/REST (Legacy/Compatibility)
- **Inference Backend**: NVIDIA Triton Inference Server
- **Serialization**: Protocol Buffers (Protobuf)
- **Infrastructure**: Docker & Docker Compose

## Project Structure
```text
.
├── gateway/          # Java/Spring Boot Gateway Implementation
│   └── sentinel/     # Gradle project root
├── infra/            # Infrastructure configuration (Triton, Docker)
├── benchmarks/       # Performance testing and latency metrics
├── triton-client/    # Client utilities and testing scripts
└── pyproject.toml    # Python project configuration
```

## Getting Started

### Prerequisites
- JDK 25
- Docker & Docker Compose
- Gradle (provided via wrapper)

### Running the Environment
1. **Start Triton Inference Server**:
   ```bash
   cd infra
   docker compose up -d
   ```
2. **Build and Run the Gateway**:
   ```bash
   cd gateway/sentinel
   ./gradlew bootRun
   ```

## Future Roadmap
- [ ] Multi-model dynamic routing
- [ ] Adaptive batching implementation
- [ ] SLA-aware priority queuing
- [ ] OpenTelemetry integration for inference tracing
