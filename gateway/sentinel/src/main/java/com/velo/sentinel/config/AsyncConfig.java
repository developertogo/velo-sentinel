package com.velo.sentinel.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "applicationTaskExecutor")
  public Executor applicationTaskExecutor() {
    // Virtual threads for ultra-high concurrency and scalability
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}