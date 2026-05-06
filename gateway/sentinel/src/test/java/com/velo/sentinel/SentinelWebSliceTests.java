package com.velo.sentinel;

import com.velo.sentinel.controller.InferenceController;
import com.velo.sentinel.model.InferenceRequest;
import com.velo.sentinel.service.DynamoBridgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SentinelWebSliceTests: Interface & Serialization Validation.
 * This suite verifies the contract between the REST layer and the service layer,
 * ensuring correct data binding and error handling.
 */
public class SentinelWebSliceTests {

    private MockMvc mockMvc;
    private DynamoBridgeService bridgeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        bridgeService = Mockito.mock(DynamoBridgeService.class);
        objectMapper = new ObjectMapper();
        
        InferenceController controller = new InferenceController(bridgeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Workflow: End-to-End API Call.
     * Enhancement: Verifies that the sessionId and modelName from the JSON payload 
     * are correctly passed to the bridge service and that the REST response 
     * matches the expected schema.
     */
    @Test
    void testInferenceEndpoint_FullPayload() throws Exception {
        when(bridgeService.infer(anyFloat(), anyString(), anyString(), any())).thenReturn(42.0f);

        // Constructor: String sessionId, String modelName, float value, boolean useAgenticOptimization
        InferenceRequest request = new InferenceRequest("user-789", "llama-3", 10.0f, true);

        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction").value(42.0))
                .andExpect(jsonPath("$.sessionId").value("user-789"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(bridgeService).infer(10.0f, "user-789", "llama-3", com.velo.sentinel.model.PriorityTier.INTERACTIVE);
    }

    /**
     * Workflow: Anonymous User Support.
     * Enhancement: Confirms that missing session IDs in the request are gracefully 
     * handled by the controller and defaulted to "anonymous" in the response.
     */
    @Test
    void testInferenceEndpoint_AnonymousSession() throws Exception {
        // modelName defaults to "simple" in the controller if null
        when(bridgeService.infer(anyFloat(), isNull(), eq("simple"), any())).thenReturn(99.0f);

        // Payload with missing sessionId and modelName
        String jsonPayload = "{ \"value\": 5.0, \"useAgenticOptimization\": false }";

        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction").value(99.0))
                .andExpect(jsonPath("$.sessionId").value("anonymous"));

        verify(bridgeService).infer(eq(5.0f), isNull(), eq("simple"), any());
    }

    /**
     * Workflow: Payload Validation.
     * Verification: Ensures that malformed JSON payloads (e.g. invalid types)
     * are rejected with a 400 Bad Request status.
     */
    @Test
    void testInvalidJson_Returns400() throws Exception {
        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"value\": \"garbage\" }"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Workflow: Total System Outage Resilience.
     * Verification: Validates that if all backend paths fail (e.g. exception from BridgeService),
     * the controller returns a 503 Service Unavailable and a structured status.
     */
    @Test
    void testTotalSystemOutage_Returns503() throws Exception {
        when(bridgeService.infer(anyFloat(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Both Triton and Dynamo are down"));

        InferenceRequest request = new InferenceRequest("user-123", "simple", 10.0f, true);

        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("BACKEND_OUTAGE"))
                .andExpect(jsonPath("$.prediction").value(0.0));
    }

    // Helper for Mockito to match nulls
    private <T> T isNull() {
        return Mockito.argThat(x -> x == null);
    }

    // Helper for Mockito eq
    private <T> T eq(T value) {
        return Mockito.eq(value);
    }
}
