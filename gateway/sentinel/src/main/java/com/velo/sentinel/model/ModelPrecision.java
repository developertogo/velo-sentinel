package com.velo.sentinel.model;

/**
 * ModelPrecision: Quantization options for inference.
 */
public enum ModelPrecision {
    FP16,   // High fidelity, standard performance
    INT8,   // Balanced
    INT4    // High performance, slightly lower fidelity
}
