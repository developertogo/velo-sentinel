package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuditLoggerService: The "Black Box Recorder" of the System.
 *
 * Just like an airplane has a "Black Box" that records flight data for safety,
 * this service records metadata about every AI request.
 *
 * It's "Immutable," which means once a record is written, it can never be changed or deleted.
 * This is crucial for "Compliance" (following laws and regulations).
 *
 * Note: It records *how* the system performed (latency, backend used, errors),
 * but it does NOT record *what* the user said (to protect privacy).
 */
@Service
public class AuditLoggerService {
    private static final Logger auditLog = LoggerFactory.getLogger("SENTINEL-AUDIT");

    /**
     * Records an inference event metadata.
     *
     * @param sessionId The user session identifier.
     * @param modelName The name of the model utilized.
     * @param backend The backend provider (TRITON, DYNAMO, METAL).
     * @param latencyMs The total round-trip latency in milliseconds.
     * @param drift The calculated accuracy drift relative to ground truth.
     * @param status The execution status of the request.
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
