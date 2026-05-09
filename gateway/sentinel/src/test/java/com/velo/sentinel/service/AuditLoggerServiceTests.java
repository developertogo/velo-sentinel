package com.velo.sentinel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerServiceTests {

    private final AuditLoggerService auditLogger = new AuditLoggerService();

    @Test
    void testLogInference() {
        // Verify that logging doesn't throw exceptions
        assertDoesNotThrow(() -> {
            auditLogger.logInference(
                "session-123",
                "llama-3-8b",
                "DYNAMO",
                45L,
                0.01,
                "SUCCESS"
            );
        });
    }
}
