# NVIDIA Dynamo-Aware Scaling

Velo-Sentinel provides the foundational metrics and control planes required to scale GPU clusters efficiently in a disaggregated inference environment.

## 📊 Backend Pressure Metrics
Traditional Horizontal Pod Autoscalers (HPA) rely on CPU or Memory utilization, which are poor indicators of GPU-bound inference pressure. Sentinel exports custom "Backend Pressure" metrics to Micrometer:

*   **Queue Depth**: Real-time count of requests waiting in the `PriorityTier` queues.
*   **SLA Headroom**: Average time remaining before requests breach their SLA thresholds.
*   **Throughput (Tokens/sec)**: Aggregated throughput across disaggregated Prefill and Decode streams.

## ⚙️ SentinelScalingController
The `SentinelScalingController` provides a dedicated REST endpoint for custom Kubernetes Operators or external scalers to retrieve high-fidelity scaling signals.

*   **Endpoint**: `/actuator/velo/scaling-metrics`
*   **Format**: JSON metrics optimized for HPA consumption.

## 🚀 Predictive Scaling (Tier-1 Readiness)
By monitoring the "Session Warmth" in the `KVCacheRegistry`, the system can predict upcoming demand for specific models.

1.  **Session Hydration**: As users begin a session, the gateway signals the scaling plane to ensure that GPU workers hosting the relevant model context are warm and have sufficient headroom.
2.  **Resource Rebalancing**: The gateway can dynamically adjust batching windows in response to cluster-wide pressure, sacrificing a few milliseconds of latency to ensure system stability during "Thundering Herd" events.

## 🏗️ Integration with Kubernetes
The scaling metrics are exposed via the `/health` and `/actuator` endpoints, allowing for seamless integration with:
*   **Prometheus Adapter**: To convert Micrometer gauges into K8s Custom Metrics.
*   **KEDA (Kubernetes Event-driven Autoscaling)**: For scaling GPU pods to zero when no sessions are active in the Redis registry.
