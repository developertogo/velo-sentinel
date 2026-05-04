package com.velo.sentinel.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AdaptiveBatcherTests {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBatcherTests.class);

    @Test
    void testSuccessfulBatching() throws Exception {
        AdaptiveBatcher batcher = new AdaptiveBatcher();
        AtomicInteger callCount = new AtomicInteger(0);

        // Submit 3 requests
        CompletableFuture<Float> f1 = batcher.submit(1.0f, "s1", "m1", items -> {
            callCount.incrementAndGet();
            return items.stream().map(i -> i.value() * 2).toList();
        });
        CompletableFuture<Float> f2 = batcher.submit(2.0f, "s2", "m1", items -> {
            callCount.incrementAndGet();
            return items.stream().map(i -> i.value() * 2).toList();
        });
        CompletableFuture<Float> f3 = batcher.submit(3.0f, "s3", "m1", items -> {
            callCount.incrementAndGet();
            return items.stream().map(i -> i.value() * 2).toList();
        });

        // Wait for results
        assertThat(f1.get(1, TimeUnit.SECONDS)).isEqualTo(2.0f);
        assertThat(f2.get(1, TimeUnit.SECONDS)).isEqualTo(4.0f);
        assertThat(f3.get(1, TimeUnit.SECONDS)).isEqualTo(6.0f);

        // Should have been processed in a single batch (callCount = 1)
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void testBatcherTimeout() throws Exception {
        AdaptiveBatcher batcher = new AdaptiveBatcher();
        
        // Submit only 1 request (won't hit maxBatchSize=16)
        CompletableFuture<Float> future = batcher.submit(5.0f, "s1", "m1", items -> {
            return items.stream().map(i -> i.value() * 10).toList();
        });

        // Should still complete after the 5ms window
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(50.0f);
    }

    @Test
    void testBatchProcessorFailure() {
        AdaptiveBatcher batcher = new AdaptiveBatcher();
        
        CompletableFuture<Float> future = batcher.submit(5.0f, "s1", "m1", items -> {
            throw new RuntimeException("Backend Down");
        });

        assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testHighConcurrencyBatching() throws Exception {
        AdaptiveBatcher batcher = new AdaptiveBatcher();
        int taskCount = 100;
        List<CompletableFuture<Float>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            float val = (float) i;
            futures.add(batcher.submit(val, "session-" + i, "model", items -> {
                log.info("Processing batch of size {}", items.size());
                return items.stream().map(item -> item.value() + 1).toList();
            }));
        }

        for (int i = 0; i < taskCount; i++) {
            assertThat(futures.get(i).get(2, TimeUnit.SECONDS)).isEqualTo((float) i + 1);
        }
    }
}
