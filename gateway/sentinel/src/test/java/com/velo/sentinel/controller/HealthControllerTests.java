package com.velo.sentinel.controller;

import com.velo.sentinel.client.DynamoGrpcClient;
import com.velo.sentinel.client.TritonGrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HealthControllerTests: Readiness Logic Validation.
 */
public class HealthControllerTests {

    private HealthController controller;
    private TritonGrpcClient tritonClient;
    private DynamoGrpcClient dynamoClient;

    @BeforeEach
    void setUp() {
        tritonClient = mock(TritonGrpcClient.class);
        dynamoClient = mock(DynamoGrpcClient.class);
        controller = new HealthController(tritonClient, dynamoClient);
    }

    @Test
    void testHealth_AllUp_Returns200() {
        when(tritonClient.checkHealth()).thenReturn(true);
        when(dynamoClient.checkHealth()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void testHealth_TritonDown_Returns503() {
        when(tritonClient.checkHealth()).thenReturn(false);
        when(dynamoClient.checkHealth()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("status")).isEqualTo("DOWN");
        assertThat(response.getBody().get("triton")).isEqualTo("DOWN");
        assertThat(response.getBody().get("dynamo")).isEqualTo("UP");
    }

    @Test
    void testHealth_DynamoDown_Returns503() {
        when(tritonClient.checkHealth()).thenReturn(true);
        when(dynamoClient.checkHealth()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("status")).isEqualTo("DOWN");
        assertThat(response.getBody().get("dynamo")).isEqualTo("DOWN");
    }
}
