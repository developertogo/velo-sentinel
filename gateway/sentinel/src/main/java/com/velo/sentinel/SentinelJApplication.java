package com.velo.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SentinelJApplication: The main entry point for the Velo-Sentinel Inference Gateway.
 * This class initializes the Spring Boot context and launches the virtual thread-based orchestration layer.
 */
@SpringBootApplication
public class SentinelJApplication {
	private SentinelJApplication() {} // Utility class

	/**
	 * Launches the Velo-Sentinel gateway.
	 * 
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		SpringApplication.run(SentinelJApplication.class, args);
	}

}
