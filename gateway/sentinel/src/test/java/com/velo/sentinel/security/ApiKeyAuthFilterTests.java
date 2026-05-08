package com.velo.sentinel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ApiKeyAuthFilterTests: Security Logic Validation.
 */
public class ApiKeyAuthFilterTests {

    private ApiKeyAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "validApiKey", "test-key-123");
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testFilter_ValidKey_Authenticates() throws Exception {
        when(request.getHeader("X-API-KEY")).thenReturn("test-key-123");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("api-user");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testFilter_InvalidKey_DoesNotAuthenticate() throws Exception {
        when(request.getHeader("X-API-KEY")).thenReturn("wrong-key");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testFilter_MissingKey_DoesNotAuthenticate() throws Exception {
        when(request.getHeader("X-API-KEY")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
