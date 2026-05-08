package com.velo.sentinel.streaming;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ResilientStreamBridgeTests {

    @Test
    void testStreamFailover_SuccessfulTransition() throws Exception {
        StreamingInferenceBackend primary = mock(StreamingInferenceBackend.class);
        StreamingInferenceBackend fallback = mock(StreamingInferenceBackend.class);
        ResilientStreamBridge bridge = new ResilientStreamBridge(primary, fallback);

        // Primary fails after 2 tokens
        when(primary.streamInfer(anyFloat(), anyString(), anyString())).thenAnswer(inv -> {
            SubmissionPublisher<StreamEvent> pub = new SubmissionPublisher<>();
            Thread.ofVirtual().start(() -> {
                try {
                    while (pub.getNumberOfSubscribers() == 0) Thread.sleep(10);
                    pub.submit(new StreamEvent("s1", 0, "P0", false));
                    Thread.sleep(50);
                    pub.submit(new StreamEvent("s1", 1, "P1", false));
                    Thread.sleep(50);
                    pub.closeExceptionally(new RuntimeException("Primary Crash"));
                } catch (Exception e) {}
            });
            return pub;
        });

        // Fallback succeeds with 3 more tokens
        when(fallback.streamInfer(anyFloat(), anyString(), anyString())).thenAnswer(inv -> {
            SubmissionPublisher<StreamEvent> pub = new SubmissionPublisher<>();
            Thread.ofVirtual().start(() -> {
                try {
                    while (pub.getNumberOfSubscribers() == 0) Thread.sleep(10);
                    pub.submit(new StreamEvent("s1", 2, "F2", false));
                    Thread.sleep(50);
                    pub.submit(new StreamEvent("s1", 3, "F3", false));
                    Thread.sleep(50);
                    pub.submit(new StreamEvent("s1", 4, "F4", true));
                    pub.close();
                } catch (Exception e) {}
            });
            return pub;
        });

        List<StreamEvent> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        bridge.executeResilientStream(5.0f, "test-session", "simple").subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamEvent item) { results.add(item); }
            @Override public void onError(Throwable t) {}
            @Override public void onComplete() { completed.set(true); }
        });

        // Wait for completion (max 2 seconds)
        long start = System.currentTimeMillis();
        while (!completed.get() && System.currentTimeMillis() - start < 2000) {
            Thread.sleep(100);
        }

        assertThat(completed.get()).isTrue();
        assertThat(results).hasSize(5);
        assertThat(results.get(0).content()).isEqualTo("P0");
        assertThat(results.get(2).content()).isEqualTo("F2");
        assertThat(results.get(4).isLast()).isTrue();
        
        verify(primary, times(1)).streamInfer(anyFloat(), anyString(), anyString());
        verify(fallback, times(1)).streamInfer(anyFloat(), anyString(), anyString());
    }
}
