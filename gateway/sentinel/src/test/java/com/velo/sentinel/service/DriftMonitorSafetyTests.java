package com.velo.sentinel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DriftMonitorSafetyTests: Verifying the Automated Safety Switch.
 */
public class DriftMonitorSafetyTests {

    private DriftMonitor driftMonitor;

    @BeforeEach
    void setUp() {
        driftMonitor = new DriftMonitor();
        ReflectionTestUtils.setField(driftMonitor, "maxViolations", 3);
        ReflectionTestUtils.setField(driftMonitor, "driftThreshold", 0.5);
    }

    @Test
    void testVeto_TripsAfterConsecutiveViolations() {
        // Initial state: Veto inactive
        assertThat(driftMonitor.isVetoActive()).isFalse();

        // 1st violation: Drift = 1.0 > 0.5
        driftMonitor.recordObservation(10.0f, 11.0f);
        assertThat(driftMonitor.isVetoActive()).isFalse();
        assertThat(driftMonitor.getViolationCount()).isEqualTo(1);

        // 2nd violation
        driftMonitor.recordObservation(10.0f, 11.0f);
        assertThat(driftMonitor.isVetoActive()).isFalse();
        assertThat(driftMonitor.getViolationCount()).isEqualTo(2);

        // 3rd violation: Should trip the veto
        driftMonitor.recordObservation(10.0f, 11.0f);
        assertThat(driftMonitor.isVetoActive()).isTrue();
        assertThat(driftMonitor.getViolationCount()).isEqualTo(3);
    }

    @Test
    void testVeto_DoesNotTripOnSmallDrift() {
        // Drift = 0.1 < 0.5
        for (int i = 0; i < 10; i++) {
            driftMonitor.recordObservation(10.0f, 10.1f);
        }
        assertThat(driftMonitor.isVetoActive()).isFalse();
        assertThat(driftMonitor.getViolationCount()).isEqualTo(0);
    }

    @Test
    void testVeto_ResetClearsState() {
        // Trip the veto
        driftMonitor.recordObservation(10.0f, 11.0f);
        driftMonitor.recordObservation(10.0f, 11.0f);
        driftMonitor.recordObservation(10.0f, 11.0f);
        assertThat(driftMonitor.isVetoActive()).isTrue();

        // Reset
        driftMonitor.resetVeto();
        assertThat(driftMonitor.isVetoActive()).isFalse();
        assertThat(driftMonitor.getViolationCount()).isEqualTo(0);
    }
}
