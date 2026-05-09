package com.velo.sentinel.streaming;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamoStreamingBackendTests {

    private final DynamoStreamingBackend backend = new DynamoStreamingBackend();

    @Test
    void testStreamInfer() throws InterruptedException {
        Flow.Publisher<StreamEvent> publisher = backend.streamInfer(1.0f, "session-1", "model-1");
        List<StreamEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamEvent item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(5, events.size());
        assertTrue(events.get(4).isLast());
    }
}
