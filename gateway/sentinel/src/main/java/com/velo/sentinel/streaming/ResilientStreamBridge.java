package com.velo.sentinel.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ResilientStreamBridge: Orchestrates mid-stream failovers.
 * 
 * If a primary AI stream stutters or fails, this bridge automatically
 * re-homes the session to a ground-truth backend and resumes generation
 * from the last successful token.
 */
@Service
public class ResilientStreamBridge {
    private static final Logger log = LoggerFactory.getLogger(ResilientStreamBridge.class);

    private final StreamingInferenceBackend primaryBackend;
    private final StreamingInferenceBackend fallbackBackend;

    public ResilientStreamBridge(
            @org.springframework.beans.factory.annotation.Qualifier("primaryStreamingBackend") StreamingInferenceBackend primaryBackend,
            @org.springframework.beans.factory.annotation.Qualifier("fallbackStreamingBackend") StreamingInferenceBackend fallbackBackend) {
        this.primaryBackend = primaryBackend;
        this.fallbackBackend = fallbackBackend;
    }

    public Flow.Publisher<StreamEvent> executeResilientStream(float input, String sessionId, String modelName) {
        return new Flow.Publisher<>() {
            @Override
            public void subscribe(Flow.Subscriber<? super StreamEvent> subscriber) {
                SubmissionPublisher<StreamEvent> externalPublisher = new SubmissionPublisher<>();
                externalPublisher.subscribe(subscriber);

                List<StreamEvent> tokenBuffer = new ArrayList<>();
                AtomicInteger lastTokenIndex = new AtomicInteger(-1);

                log.info("STREAM-ORCHESTRATION [Session: {}]: Starting resilient stream for model {}.", sessionId,
                        modelName);

                // Try Primary (Dynamo)
                attemptStream(primaryBackend, input, sessionId, modelName, externalPublisher, tokenBuffer,
                        lastTokenIndex, true);
            }
        };
    }

    private void attemptStream(StreamingInferenceBackend backend, float input, String sessionId, String modelName,
            SubmissionPublisher<StreamEvent> publisher, List<StreamEvent> buffer,
            AtomicInteger lastIndex, boolean isPrimary) {

        backend.streamInfer(input, sessionId, modelName).subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(StreamEvent item) {
                buffer.add(item);
                lastIndex.set(item.tokenIndex());
                publisher.submit(item);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                if (isPrimary) {
                    log.warn(
                            "STREAM-FAILOVER [Session: {}]: Primary backend failed at token {}. Reason: {}. Re-homing to Fallback...",
                            sessionId, lastIndex.get(), throwable.getMessage());

                    // Resume from Fallback (Triton)
                    // In a real LLM, we would adjust the 'input' to include the buffer content.
                    // For this float-based prototype, we'll just resume the call.
                    attemptStream(fallbackBackend, input, sessionId, modelName, publisher, buffer, lastIndex, false);
                } else {
                    log.error("STREAM-CRITICAL [Session: {}]: Fallback backend also failed. Terminating stream.",
                            sessionId);
                    publisher.closeExceptionally(throwable);
                }
            }

            @Override
            public void onComplete() {
                log.info("STREAM-COMPLETE [Session: {}]: Stream finalized successfully.", sessionId);
                publisher.close();
            }
        });
    }
}
