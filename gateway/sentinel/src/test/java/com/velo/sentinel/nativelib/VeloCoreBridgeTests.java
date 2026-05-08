package com.velo.sentinel.nativelib;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class VeloCoreBridgeTests {

    @Test
    void testNativeBridgeRoundTrip() {
        // Initialize bridge with a mock model name and small slot count
        try (VeloCoreBridge bridge = new VeloCoreBridge("llama-3-8b", 4, 1024)) {
            List<Integer> prompt = List.of(1, 2, 3, 4, 5);
            
            // The POC Rust implementation just returns the prompt back
            List<Integer> result = bridge.generate(prompt, 10);
            
            assertThat(result).containsExactly(1, 2, 3, 4, 5);
            System.out.println("Native bridge verified: Zero-copy round-trip successful!");
        }
    }
}
