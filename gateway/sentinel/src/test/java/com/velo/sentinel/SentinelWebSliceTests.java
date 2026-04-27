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

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SentinelWebSliceTests {

    private MockMvc mockMvc;
    private DynamoBridgeService bridgeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        bridgeService = Mockito.mock(DynamoBridgeService.class);
        objectMapper = new ObjectMapper();
        
        // Manual standalone setup of MockMvc (The "Universal" Way)
        InferenceController controller = new InferenceController(bridgeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testInferenceEndpointMapping() throws Exception {
        when(bridgeService.infer(anyFloat(), anyString())).thenReturn(42.0f);

        InferenceRequest request = new InferenceRequest("test-session", 5.0f, false);

        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction").value(42.0))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void testInvalidRequestReturnsBadRequest() throws Exception {
        // Standalone setup doesn't have all the global error handling by default, 
        // but it will still fail on type mismatches or missing bodies.
        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"value\": \"not-a-number\" }"))
                .andExpect(status().isBadRequest());
    }
}
