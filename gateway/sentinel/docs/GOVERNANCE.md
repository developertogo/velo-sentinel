# Enterprise Governance & Privacy

Velo-Sentinel provides a robust governance layer to ensure that AI adoption meets strict regulatory and security requirements. By intercepting every request at the gateway, the system enforces privacy and compliance before data reaches the inference backends.

## 🛡️ PII Scrubbing (Privacy-by-Design)
The `PrivacyScrubberService` automatically redacts sensitive user data from text prompts before they are transmitted to the GPU cluster.

*   **High Performance**: Uses pre-compiled, optimized regex and NLP patterns to ensure minimal latency overhead.
*   **Automatic Redaction**: Detects and redacts:
    *   Names and Personal Identifiers
    *   Email Addresses and Phone Numbers
    *   Social Security Numbers (SSNs) and Credit Card numbers
*   **Enterprise Boundary**: Ensures that sensitive data never leaves the internal security boundary, protecting against accidental data leakage to cloud-based LLM providers.

## 📜 Immutable Audit Logging
The `AuditLoggerService` provides a tamper-proof "paper trail" for every inference decision made by the gateway.

*   **Compliance-Ready**: Captured metadata includes:
    *   Session ID and Request Origin
    *   Target Model and Backend Selection (Triton vs. Dynamo)
    *   Latency and Throughput metrics
    *   Accuracy Drift and SLA violations
*   **Structured Format**: Logs are stored in a write-only, structured JSON format, ready for ingestion into ELK (Elasticsearch/Logstash/Kibana) or Splunk for regulatory audits.

## 🔐 Authentication & API Security
Standardized access control ensures that only authorized applications can utilize GPU resources.

*   **API Key Validation**: Integrated Spring Security with custom `ApiKeyAuthFilter` for `X-API-KEY` header validation.
*   **Stateless Security**: Optimized for high-throughput, stateless inference workloads.
*   **Granular Access**: Ability to define per-session rate limits and priority tiers based on the authenticated principal.
