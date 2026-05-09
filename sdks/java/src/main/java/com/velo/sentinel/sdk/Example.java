package com.velo.sentinel.sdk;

import com.velo.sentinel.sdk.model.InferenceRequest;
import com.velo.sentinel.sdk.model.InferenceResponse;

/**
 * Example: Demonstrates usage of the Resilient Java SDK.
 */
public class Example {
    private Example() {} // Utility class

    /**
     * Runs the SDK usage demonstration.
     * 
     * @param args Command line arguments (not used).
     * @throws Exception If any error occurs during inference.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Velo-Sentinel Java SDK Example");
        System.out.println("--------------------------------");

        // 1. Initialize the resilient client
        // Params: baseUrl, hedgingDelayMs (50ms), maxRetries (3)
        ResilientSentinelClient client = new ResilientSentinelClient("http://localhost:8080", 50, 3);

        try {
            // 2. Prepare a request
            InferenceRequest request = new InferenceRequest(
                "java-client-01",
                "llama-3-8b",
                42.0f,
                false
            );

            // 3. Execute synchronous inference
            System.out.println("DEBUG: Sending synchronous request...");
            InferenceResponse response = client.infer(request);

            System.out.println("SUCCESS: Prediction: " + response.prediction() + 
                               " | Status: " + response.status());

            // 4. Execute asynchronous inference with hedging
            System.out.println("DEBUG: Sending async hedged request...");
            client.inferAsync(request).thenAccept(res -> {
                System.out.println("ASYNC-SUCCESS: Prediction: " + res.prediction());
            }).join();

        } finally {
            client.shutdown();
            System.out.println("--------------------------------");
            System.out.println("✅ Example execution complete.");
        }
    }
}
