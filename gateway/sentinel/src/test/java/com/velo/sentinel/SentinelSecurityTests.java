package com.velo.sentinel;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.client.TritonGrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SentinelSecurityTests: Enterprise Access Control Validation.
 */
@SpringBootTest
public class SentinelSecurityTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TritonGrpcClient tritonClient;

    @Autowired
    private DynamoGrpcClient dynamoClient;

    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TritonGrpcClient tritonGrpcClient() {
            return Mockito.mock(TritonGrpcClient.class);
        }

        @Bean
        @Primary
        public DynamoGrpcClient dynamoGrpcClient() {
            return Mockito.mock(DynamoGrpcClient.class);
        }
    }

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Mock health checks to return true by default
        when(tritonClient.checkHealth()).thenReturn(true);
        when(dynamoClient.checkHealth()).thenReturn(true);
        
        // Mock inference calls to avoid gRPC errors
        when(tritonClient.infer(anyFloat())).thenReturn(null); // Or mock a real response if needed
        when(dynamoClient.callDynamo(anyFloat(), anyString(), anyString())).thenReturn(1.0f);
    }

    @Test
    void testInferEndpoint_Unauthorized_Returns403() throws Exception {
        mockMvc.perform(post("/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"value\": 1.0 }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testInferEndpoint_Authorized_ReturnsOk() throws Exception {
        mockMvc.perform(post("/infer")
                .header("X-API-KEY", "velo-admin-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"value\": 1.0, \"sessionId\": \"test-session\", \"modelName\": \"simple\", \"useAgenticOptimization\": false, \"priority\": \"INTERACTIVE\" }"))
                .andExpect(status().isOk());
    }

    @Test
    void testHealthEndpoint_Public_ReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
