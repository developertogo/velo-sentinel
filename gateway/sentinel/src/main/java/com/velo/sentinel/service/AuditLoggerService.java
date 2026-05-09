package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuditLoggerService: Immutable Compliance Logger.
 * 
 * Streams inference metadata (excluding PII payloads) to a secure, write-only
 * log for regulatory compliance and audit trails.
 */
@Service
public class AuditLoggerService {
    private static final Logger auditLog = LoggerFactory.getLogger("SENTINEL-AUDIT");

    /**
     * Records an inference event metadata.
     */
    public void logInference(String sessionId, String modelName, String backend, long latencyMs, double drift, String status) {
        Map<String, Object> metadata = new ConcurrentHashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("session_id", sessionId);
        metadata.put("model", modelName);
        metadata.put("backend", backend);
        metadata.put("latency_ms", latencyMs);
        metadata.put("drift", drift);
        metadata.put("status", status);

        // In a production scenario, this would be sent to a Kinesis stream or a WORM (Write Once Read Many) storage.
        // Here we use a dedicated logger that can be routed to a secure file.
        auditLog.info("AUDIT-RECORD: {}", metadata);
    }
}
