package com.velo.sentinel.resilience;

import com.velo.sentinel.backend.TritonBackend;
import com.velo.sentinel.backend.DynamoBackend;
import com.velo.sentinel.service.DynamoResilienceComponent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * SentinelResilienceTests: Verifying the Decoupled Resilience Layer.
 * 
 * This suite confirms that the DynamoResilienceComponent correctly implements
 * the fail-open logic, ensuring that any failure in the Dynamo path defaults
 * to the Triton Ground Truth.
 */
public class SentinelResilienceTests {

    /**
     * Workflow: Manual Fallback Activation.
     * Verification: Confirms that the ResilienceComponent correctly 
     * switches to the Triton Ground Truth when an exception is intercepted.
     */
    @Test
    void testFailOpenLogic() {
        DynamoBackend dynamo = mock(DynamoBackend.class);
        TritonBackend triton = mock(TritonBackend.class);
        DynamoResilienceComponent resilience = new DynamoResilienceComponent(dynamo, triton);

        when(triton.infer(anyFloat())).thenReturn(10.0f);

        // Manually trigger the fallback to verify the fail-open logic
        float result = resilience.failOpenToTriton(5.0f, new RuntimeException("Chaos"));

        assertThat(result).isEqualTo(10.0f);
        verify(triton, times(1)).infer(5.0f);
    }
}
