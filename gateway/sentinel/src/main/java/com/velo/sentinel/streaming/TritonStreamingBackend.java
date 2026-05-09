package com.velo.sentinel.streaming;

import org.springframework.stereotype.Service;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * TritonStreamingBackend: Implementation of token-by-token generation for legacy Triton.
 * (Mocked for demonstration).
 */
@Service("fallbackStreamingBackend")
public class TritonStreamingBackend implements StreamingInferenceBackend {

    @Override
    public Flow.Publisher<StreamEvent> streamInfer(float input, String sessionId, String modelName) {
        SubmissionPublisher<StreamEvent> publisher = new SubmissionPublisher<>();
        
        // Simulate a stream starting from a potentially higher index if called during failover
        Thread.ofVirtual().start(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(50);
                    publisher.submit(new StreamEvent(sessionId, i, "Triton-Token-" + i, i == 9));
                }
                publisher.close();
            } catch (Exception e) {
                publisher.closeExceptionally(e);
            }
        });
        
        return publisher;
    }
}
