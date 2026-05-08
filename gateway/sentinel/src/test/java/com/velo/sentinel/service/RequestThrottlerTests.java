package com.velo.sentinel.service;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RequestThrottlerTests: Validating SLA Enforcement.
 */
public class RequestThrottlerTests {

    private RequestThrottler throttler;

    @BeforeEach
    void setUp() {
        throttler = new RequestThrottler();
    }

    @Test
    void testThrottle_WithinQuota_ExecutesTask() {
        AtomicInteger counter = new AtomicInteger(0);
        String sessionId = "session-1";

        String result = throttler.throttle(sessionId, () -> {
            counter.incrementAndGet();
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void testThrottle_ExceedsQuota_ThrowsException() {
        String sessionId = "abusive-session";
        
        // Quota is 10 requests per second
        for (int i = 0; i < 10; i++) {
            throttler.throttle(sessionId, () -> "ok");
        }

        // 11th request should fail
        assertThatThrownBy(() -> 
            throttler.throttle(sessionId, () -> "fail")
        ).isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void testThrottle_Isolation_SessionsDoNotInterfere() {
        String sessionA = "user-a";
        String sessionB = "user-b";

        // Saturate Session A
        for (int i = 0; i < 10; i++) {
            throttler.throttle(sessionA, () -> "ok");
        }

        // Session A should be blocked
        assertThatThrownBy(() -> 
            throttler.throttle(sessionA, () -> "fail")
        ).isInstanceOf(RequestNotPermitted.class);

        // Session B should still be allowed (Isolation)
        String result = throttler.throttle(sessionB, () -> "allowed");
        assertThat(result).isEqualTo("allowed");
    }
}
