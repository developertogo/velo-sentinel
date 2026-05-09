# Resilience & Chaos Simulation

Velo-Sentinel is hardened to be "Unbreakable" under distributed failure conditions. The system employs a multi-layered fault tolerance model to ensure that no request ever receives an unhandled error.

## 🛡️ Layered Resilience Model
Every failure scenario has a defined, tested fallback path.

| Scenario | Condition | Failover Behavior |
|---|---|---|
| **Dynamo Backend Crash** | `DYNAMO` or `SHADOW` mode; Dynamo gRPC throws | `DynamoResilienceComponent` circuit breaker trips → fall back to **Triton** |
| **Triton Ground Truth Loss** | `SHADOW` mode; Triton `StructuredTaskScope` fails or times out | Fall back to **Dynamo** as a best-effort prediction |
| **Redis KV-Cache Outage** | `KVCacheRegistry` throws `RedisConnectionFailureException` | **Fail-Open** → treat every session as Cold Start; inference continues |
| **Total System Outage** | Both Dynamo and Triton backends are unreachable | **Fail-Closed** → returns `503 Service Unavailable` with `BACKEND_OUTAGE` status |
| **Batching Failure** | `AdaptiveBatcher` timeout or processor exception | Fall back to a **direct, individual** `protectedDynamoCall` |
| **SLA Deadline Exceeded** | Task sits in priority queue past its `PriorityTier` SLA | **SLA-Veto**: task is dropped with `TimeoutException`; GPU cycles saved |

## 🧪 Chaos Engineering (Fault Injection)
The gateway includes an integrated Chaos Engine to prove the robustness of its circuit breakers and fallback paths.

### Running Chaos Simulation
1. **Trigger Fail-Open (Simulate Backend Crash)**:
   Use a session ID starting with `chaos-fail` to trigger a hard error in the Mock server.
   ```bash
   curl -X POST http://localhost:8080/infer -H "Content-Type: application/json" -d '{"sessionId": "chaos-fail-01", "value": 10}'
   ```
   *Expected Result*: Status `SUCCESS`, prediction `10.0` (Automated Fallback).

2. **Trigger SLO Veto (Simulate High Latency)**:
   Use a session ID starting with `chaos-slow` in `SHADOW` mode.
   ```bash
   curl -X POST http://localhost:8080/infer -H "Content-Type: application/json" -d '{"sessionId": "chaos-slow-01", "value": 10}'
   ```
   *Expected Result*: Status `SUCCESS`, prediction `10.0` (Triton Ground Truth). Check logs for `SHADOW-VETO`.

## 🔄 Failover Decision Tree (Shadow Mode)
```text
SHADOW request received
    └─ Triton scope succeeds?
        ├─ YES → return Triton result; compare Dynamo async (zero latency impact)
        └─ NO  → Dynamo best-effort via resilienceComponent
                    └─ Dynamo also fails?
                        └─ resilienceComponent circuit breaker → Triton direct call
```
