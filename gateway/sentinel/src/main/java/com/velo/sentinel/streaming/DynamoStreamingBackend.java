package com.velo.sentinel.streaming;

import org.springframework.stereotype.Service;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Mock Dynamo Streaming Backend.
 */
@Service("primaryStreamingBackend")
public class DynamoStreamingBackend implements StreamingInferenceBackend {

    @Override
    public Flow.Publisher<StreamEvent> streamInfer(float input, String sessionId, String modelName) {
        SubmissionPublisher<StreamEvent> publisher = new SubmissionPublisher<>();
        
        // Simulate a stream of 5 tokens
        Thread.ofVirtual().start(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(100);
                    publisher.submit(new StreamEvent(sessionId, i, "Token-" + i, i == 4));
                }
                publisher.close();
            } catch (Exception e) {
                publisher.closeExceptionally(e);
            }
        });
        
        return publisher;
    }
}
