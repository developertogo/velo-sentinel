# Velo-Sentinel Observability Guide

This document describes the observability features implemented to monitor Velo-Sentinel in high-scale production environments.

## 📊 Structured JSON Logging

Velo-Sentinel is configured to emit logs in **Logstash JSON format**, optimized for ingestion by ELK (Elasticsearch, Logstash, Kibana), Splunk, or Datadog.

### Log Enrichment (MDC)
Every log line is automatically enriched with the following context:
*   **sessionId**: The unique identifier for the user session (captured via `ScopedValue`).
*   **thread_name**: Indicates if the request is running on a **Virtual Thread**.
*   **traceId / spanId**: Correlated with OpenTelemetry for distributed tracing.

### Configuration
Logging is managed via `src/main/resources/logback-spring.xml`.
*   **Pattern**: JSON
*   **Levels**: Defaulted to `INFO`. Critical outages are logged at `ERROR` with stack traces.

---

## 🩺 Health & Readiness Probes

For zero-downtime deployments (Kubernetes), Velo-Sentinel provides a dedicated health signaling system.

### Readiness Endpoint: `/health`
The gateway exposes a custom health endpoint that aggregates the status of its backend dependencies.

*   **Endpoint**: `GET /health`
*   **Logic**: Returns **HTTP 200 OK** only if both Triton and Dynamo backends are reachable.
*   **Failure**: Returns **HTTP 503 Service Unavailable** if any backend is down.

**Example Response:**
```json
{
  "status": "UP",
  "triton": "UP",
  "dynamo": "UP"
}
```

---

## 🏎️ gRPC Load Balancing

Velo-Sentinel implements **Client-Side Load Balancing** to ensure high throughput across distributed GPU clusters.

*   **Resolver**: `dns:///` (Headless Service Discovery)
*   **Policy**: `round_robin`
*   **Impact**: Even with long-lived gRPC connections, requests are balanced across all available backend pods, preventing hot-spotting on a single GPU node.
