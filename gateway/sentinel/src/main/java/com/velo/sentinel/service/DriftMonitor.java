package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DriftMonitor: The Automated Safety Switch.
 * 
 * Monitors the accuracy drift between Triton (Ground Truth) and Dynamo (Shadow).
 * If drift exceeds 'maxDriftThreshold' for more than 'maxViolations' times,
 * the monitor trips the "Veto" switch to disable Dynamo path automatically.
 */
@Service
public class DriftMonitor {
    private static final Logger log = LoggerFactory.getLogger(DriftMonitor.class);

    private final AtomicInteger violationCounter = new AtomicInteger(0);
    private final AtomicBoolean vetoActive = new AtomicBoolean(false);

    @Value("${velo.sentinel.drift.max-violations:10}")
    private int maxViolations;

    @Value("${velo.sentinel.drift.threshold:0.1}")
    private double driftThreshold;

    /**
     * Records a prediction pair and checks for drift.
     * 
     * @param tritonValue Ground truth from legacy backend.
     * @param dynamoValue Experimental value from next-gen backend.
     */
    public void recordObservation(float tritonValue, float dynamoValue) {
        if (vetoActive.get()) return;

        double drift = Math.abs(tritonValue - dynamoValue);
        
        if (drift > driftThreshold) {
            int currentViolations = violationCounter.incrementAndGet();
            log.warn("DRIFT-VIOLATION [{}]: Drift detected ({}). Current violations: {}/{}", 
                drift > driftThreshold, drift, currentViolations, maxViolations);
            
            if (currentViolations >= maxViolations) {
                tripVeto();
            }
        } else {
            // Decaying violations on success to avoid false positives from transient noise
            violationCounter.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    /**
     * Trips the safety switch, disabling the Dynamo path.
     */
    private void tripVeto() {
        if (vetoActive.compareAndSet(false, true)) {
            log.error("SAFETY-SWITCH-TRIPPED: Dynamo model drift is too high. Vetoing Dynamo path and failing back to TRITON ground truth.");
        }
    }

    /**
     * Resets the safety switch (Manual intervention required).
     */
    public void resetVeto() {
        vetoActive.set(false);
        violationCounter.set(0);
        log.info("SAFETY-SWITCH-RESET: Drift monitor has been manually reset.");
    }

    public boolean isVetoActive() {
        return vetoActive.get();
    }
    
    public int getViolationCount() {
        return violationCounter.get();
    }
}
