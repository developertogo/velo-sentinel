package com.velo.sentinel.bench;

import com.velo.sentinel.context.InferenceContext;
import com.velo.sentinel.service.AdaptiveBatcher;
import com.velo.sentinel.model.PriorityTier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class SentinelBenchmarks {

    private AdaptiveBatcher batcher;
    private Function<List<AdaptiveBatcher.BatchItem>, List<Float>> nullProcessor;

    @Setup
    public void setup() {
        batcher = new AdaptiveBatcher(new SimpleMeterRegistry());
        nullProcessor = items -> items.stream().map(i -> 0.0f).toList();
    }

    @TearDown
    public void tearDown() {
        batcher.shutdown();
    }

    @Benchmark
    public Object testAdaptiveBatcherSubmitOverhead() throws Exception {
        return batcher.submit(1.0f, "bench-session", "simple", PriorityTier.BACKGROUND, true, nullProcessor);
    }

    @Benchmark
    public Object testScopedContextOverhead() throws Exception {
        return InferenceContext.runInContext("bench-session", () -> {
            return "ok";
        });
    }
}
