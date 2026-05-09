package com.velo.sentinel.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AsyncConfig: Configuration for asynchronous execution within the gateway.
 * 
 * This class enables Spring's @Async support and configures the primary
 * task executor to use Java 25 Virtual Threads for maximum scalability.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Primary task executor for the application.
   * 
   * @return An executor that spawns a new Virtual Thread for each submitted task.
   */
  @Bean(name = "applicationTaskExecutor")
  public Executor applicationTaskExecutor() {
    // Virtual threads for ultra-high concurrency and scalability
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}