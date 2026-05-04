package com.velo.sentinel.config;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncConfigTests {

    @Test
    void testApplicationTaskExecutor() {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.applicationTaskExecutor();
        assertThat(executor).isNotNull();
    }
}
