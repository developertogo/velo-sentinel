package com.velo.sentinel.model;

/**
 * ModelPrecision: Quantization options for inference.
 */
public enum ModelPrecision {
    /** High fidelity, standard performance. */
    FP16,
    
    /** Balanced performance and accuracy. */
    INT8,
    
    /** Maximum performance, slightly lower fidelity. */
    INT4
}
