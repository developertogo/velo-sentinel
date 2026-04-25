# Velo Sentinel

> High-performance Java 25 Virtual Thread-based inference gateway serving as the critical bridge for transitioning from legacy NVIDIA Triton to the **NVIDIA Dynamo 1.0** disaggregated inference framework.

## Mission
To provide a production-grade, high-concurrency ML model serving gateway that abstracts backend complexity while ensuring strict SLA compliance through intelligent request orchestration.

## Key Objectives
- **Legacy-to-Modern Bridge**: A robust inference gateway built on **Spring Boot 4** and **Java 25**, serving as the critical path for transitioning legacy **NVIDIA Triton** workloads to the **NVIDIA Dynamo 1.0** disaggregated architecture.
- **Orchestration Layer**: A sophisticated model orchestration layer that enables scalable routing and multi-model serving architectures.
- **High-Concurrency Execution**: Leveraging **Java Virtual Threads** (Project Loom) to achieve low-latency and high-throughput request processing, even under heavy distributed loads.
- **Type-Safe Contract System**: A **protobuf-based gRPC contract system** ensuring strict schema enforcement and automated stub generation for reliable cross-service communication.
- **Extensible Architecture**: A clean service boundary design that separates the API Gateway, Inference Orchestration, and Model Execution layers.

## Architectural Principles
Velo Sentinel follows modern high-performance architectural patterns. The system prioritizes **Structured Concurrency** and **Java 25 Virtual Threads** over legacy Reactive patterns, utilizing **Bounded Executors** to ensure service resilience and protect the NVIDIA Dynamo-Triton backend from distributed request spikes.

## Technology Stack
- **Language**: Java 25 (Optimized for Virtual Threads)
- **Framework**: Spring Boot 4.0.5
- **Communication**: gRPC (Primary) & HTTP/REST (Legacy/Compatibility)
- **Inference Backend**: NVIDIA Dynamo-Triton
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
1. **Start NVIDIA Dynamo-Triton**:
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

## Intellectual Property
This project was designed and implemented by Carlos Hung as a technical demonstration of high-performance system architecture. All architectural decisions, performance optimizations, and code implementations are original work.
