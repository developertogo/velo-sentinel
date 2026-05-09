# Multi-Cloud Disaster Recovery (DR)

Velo-Sentinel is designed for Tier-1 availability (99.99%+), ensuring that inference services remain operational even during total cloud region outages.

## 🌍 Cross-Cloud Failover
The gateway supports a seamless `FAILOVER` routing mode that can shift traffic from the primary cloud provider (e.g., AWS) to a secondary standby cluster (e.g., GCP or Azure) in milliseconds.

### Implementation Details
*   **StandbyBackend**: A dedicated inference backend that manages high-resiliency connections to a secondary region.
*   **StandbyTritonClient**: A pre-warmed gRPC client that maintains persistent, cross-region connections to prevent "cold-start" connectivity issues during a disaster.
*   **Health Check Monitoring**: The gateway continuously monitors the health of both primary and secondary clusters to ensure readiness.

## ⚙️ Configuration
Failover parameters can be configured in `application.yaml` or via environment variables for dynamic response to outages:

```yaml
triton:
  standby:
    host: ${STANDBY_HOST:secondary.cloud.provider}
    port: ${STANDBY_PORT:8001}
```

## 🚀 Activation
To trigger an emergency failover, update the gateway routing mode:

```bash
# Set routing mode to FAILOVER via command line argument
./gradlew bootRun --args='--velo.sentinel.routing-mode=FAILOVER'
```

## 🛡️ Resilience Logic
The standby path utilizes a strict **500ms deadline** and dedicated error handling to ensure that if the standby region is also under stress, the gateway can quickly report a `BACKEND_OUTAGE` rather than hanging the user's connection.
