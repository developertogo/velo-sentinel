package com.velo.sentinel.sdk.model;

/**
 * PriorityTier: Defines the Service Level Agreement (SLA) for inference requests.
 */
public enum PriorityTier {
    /** Ultra-low latency requirements (e.g. trading, live voice). SLA: 100ms. */
    REALTIME(100),
    
    /** Standard user interaction (e.g. chatbots). SLA: 500ms. */
    INTERACTIVE(500),
    
    /** Bulk processing or background tasks. SLA: 5000ms. */
    BACKGROUND(5000);

    private final long slaMs;

    PriorityTier(long slaMs) {
        this.slaMs = slaMs;
    }

    /**
     * Gets the SLA duration in milliseconds.
     * 
     * @return The maximum allowed latency for this tier.
     */
    public long getSlaMs() {
        return slaMs;
    }
}
