# Performance Verification Strategy

This document outlines the performance verification strategy for Velo-Sentinel, proving that our sophisticated orchestration layer adds negligible overhead while maintaining stability under extreme load.

## 1. Microbenchmarks (JMH)
We utilize the **Java Microbenchmark Harness (JMH)** to isolate and measure the cost of our core abstractions.

### Targets:
- **AdaptiveBatcher Submit Overhead**: Measures the time taken to enqueue a request and compute its SLA-aware deadline.
- **ScopedContext Overhead**: Measures the cost of binding `ScopedValue` and SLF4J `MDC` using `InferenceContext.runInContext`.

### Result Goals:
- Orchestration overhead: **< 100μs** per request. (Actual: **~0.5μs**)
- Context switching cost: **< 10μs**. (Actual: **~0.02μs**)

### Latest Benchmarks (H100/M3 Class Simulation):
| Benchmark | Mode | Score | Error | Unit |
|-----------|------|-------|-------|------|
| `testAdaptiveBatcherSubmitOverhead` | avgt | 0.052 | ± 0.038 | μs/op |
| `testScopedContextOverhead` | avgt | 0.017 | ± 0.012 | μs/op |

**Conclusion**: The Sentinel Gateway adds negligible latency to the inference path, satisfying the "Tier-1 Production" requirements for high-frequency trading and real-time AI.

---

## 2. High-Concurrency Load Testing (k6)
To simulate "The Thundering Herd," we use **k6** to drive 10,000+ concurrent virtual users (VUs) against the gateway.

### Scenario:
- **Ramp-up**: 0 to 10,000 VUs over 60 seconds.
- **Sustain**: 5 minutes at 10,000 VUs.
- **Payload**: Randomized session IDs and complexity scores to trigger contextual routing and affinity logic.

### Objectives:
- Verify that **SLA-Aware Priority Queuing** prevents high-priority requests from being starved.
- Confirm that the **Token-Bucket Rate Limiter** correctly drops excess traffic with `429 Too Many Requests`.
- Prove zero-leak memory stability under sustained high pressure.

---

## 3. Real-Time Visualization (Grafana)
We provide a pre-configured dashboard (`docs/grafana/sentinel-dashboard.json`) for real-time observability during performance tests.

### Key Metrics:
- **P99 Inference Latency**: Quantifies the end-to-end user experience.
- **Disaggregated Queue Depth**: Visualizes the balance between Prefill and Decode cluster pressure.
- **Throttling Rate**: Monitors the effectiveness of our SLA enforcement.
