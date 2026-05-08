# Velo-Sentinel Security & SLA Guide

This document outlines the security architecture and Service Level Agreement (SLA) enforcement mechanisms in Velo-Sentinel.

## 🔐 API Authentication

Velo-Sentinel uses **API Key Authentication** to protect inference resources.

### X-API-KEY Header
All requests to the `/infer` endpoint must include a valid API key in the `X-API-KEY` header.

*   **Header Name**: `X-API-KEY`
*   **Configuration**: The valid key is configured via the `velo.sentinel.api-key` property (default: `velo-admin-123`).
*   **Unauthorized Access**: Requests with missing or invalid keys will return an **HTTP 401 Unauthorized** (or 403 Forbidden).

### Future Roadmap
For production environments, the `ApiKeyAuthFilter` can be extended to validate keys against:
1.  **Redis**: For high-performance distributed validation.
2.  **PostgreSQL**: For persistent account management.
3.  **JWT**: For OAuth2/OIDC integration.

---

## 🚦 SLA Enforcement (Rate Limiting)

To prevent resource exhaustion and ensure fair allocation of GPU time, Velo-Sentinel implements **Per-Session Throttling**.

### Token-Bucket Algorithm
We use the **Resilience4j RateLimiter** to manage request quotas.

*   **Default Quota**: 10 requests per second per session.
*   **Isolation**: Throttling is applied per `sessionId`. A flood of requests from one user will not affect others.
*   **Throttled Response**: If a quota is exceeded, the gateway returns **HTTP 429 Too Many Requests** with the status `SLA_VIOLATED`.

### Monitoring Throttling
Throttling events are logged with the `SLA-VIOLATION` tag and the associated `sessionId` for audit purposes.

---

## 🏗️ Technical Stack
*   **Spring Security**: Handles the filter chain and request protection.
*   **Resilience4j**: Powers the token-bucket rate limiter.
*   **Jakarta Filters**: Used for low-latency header extraction.
