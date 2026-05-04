package com.velo.sentinel.model;

public enum PriorityTier {
    REALTIME(100),
    INTERACTIVE(500),
    BACKGROUND(5000);

    private final long slaMs;

    PriorityTier(long slaMs) {
        this.slaMs = slaMs;
    }

    public long getSlaMs() {
        return slaMs;
    }
}
