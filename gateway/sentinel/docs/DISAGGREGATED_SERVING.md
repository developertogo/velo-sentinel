# Velo-Sentinel: Disaggregated Serving & Cache-Aware Routing

This document outlines the architecture for high-performance LLM serving, optimizing for NVIDIA-scale workloads.

## 1. Disaggregated Serving (Prefill/Decode Separation)

LLM inference has two distinct phases with different compute/memory profiles:
- **Prefill**: Compute-bound. Processes the initial prompt. Efficient with large batch sizes.
- **Decode**: Memory-bandwidth bound. Generates tokens one by one. Efficient with low-latency, high-frequency execution.

### Implementation: Dual-Queue Adaptive Batching
In `AdaptiveBatcher.java`, we separate requests into two specialized priority queues:
- `prefillQueue`: Targets larger batches (32) and longer latency windows (10ms) to maximize GPU TFLOPS.
- `decodeQueue`: Targets tight latency (2ms) and smaller batches (8) to minimize Time-To-First-Token (TTFT) and Inter-Token Latency (ITL).

Requests are identified as `Prefill` if no KV-cache exists in the `KVCacheRegistry` for the given session.

## 2. Cache-Aware Routing (Sticky Affinity)

Moving large KV-caches across a network is a significant bottleneck. To mitigate this, we implement **Sticky Cache Routing**.

### Workflow:
1. **Initial Request (Cold)**: The gateway routes the request to an available `Dynamo-Worker`.
2. **Registration**: Once processed, the `KVCacheRegistry` (Redis-backed) stores the mapping: `sessionId -> workerNodeId`.
3. **Subsequent Requests (Warm)**: The gateway queries the registry. If a `workerNodeId` is found, the request is prioritized for that specific node.
4. **Latency Gain**: By reusing the local KV-cache, we avoid NIXL transfer overhead, reducing latency by up to 40% for long-context sessions.

## 3. Dynamo-Aware K8s Orchestration

The `SentinelScalingController` exposes predictive metrics for a custom Kubernetes Operator.

### Metrics Exporter:
- **Concurrency Score**: Real-time load factor.
- **Backpressure Factor**: Calculated from queue depths in the `AdaptiveBatcher`.
- **Recommended Replicas**: A predictive signal based on arrival rate and queue growth.

This allows the K8s Operator to perform **Proactive GPU Rebalancing** before the system hits a saturation point.

---

## 4. Native Bridge (FFM API)

To achieve zero-copy orchestration between the Java Gateway and the Rust `Velo-Core` engine:
- **FFI**: A C-compatible entry point in Rust (`src/ffi.rs`).
- **FFM (Project Panama)**: Uses `MemorySegment` and `Arena` in Java 25 to manage native memory directly.
- **Performance**: Eliminates the 2-5ms overhead of gRPC/IPC for local accelerated inference.
