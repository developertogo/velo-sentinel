package com.velo.sentinel.streaming;

import java.util.concurrent.Flow;

/**
 * StreamingInferenceBackend: Interface for token-by-token generation.
 */
public interface StreamingInferenceBackend {
    
    /**
     * Starts a streaming inference session.
     * 
     * @param input The input value (prompt).
     * @param sessionId Session identifier.
     * @param modelName Model identifier.
     * @return A Publisher of StreamEvents.
     */
    Flow.Publisher<StreamEvent> streamInfer(float input, String sessionId, String modelName);
}
