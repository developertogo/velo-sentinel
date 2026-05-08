package com.velo.sentinel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * ApiKeyAuthFilter: Guarding the GPU Runtime.
 * 
 * Intercepts requests to validate the 'X-API-KEY' header.
 * Bridges the gap between raw HTTP requests and Spring Security context.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    @Value("${velo.sentinel.api-key:velo-admin-123}")
    private String validApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestKey = request.getHeader(API_KEY_HEADER);

        if (validApiKey.equals(requestKey)) {
            // Populate Security Context for authorized request
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "api-user", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
            
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
