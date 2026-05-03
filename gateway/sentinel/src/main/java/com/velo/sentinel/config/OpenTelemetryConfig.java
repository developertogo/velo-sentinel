package com.velo.sentinel.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetryConfig: The Observability Backbone.
 * 
 * Initializes the OpenTelemetry SDK with autoconfiguration support.
 * 
 * LOG SUPPRESSION:
 * If no OTLP collector is found on 4317, OTel spams connection errors in background threads.
 * This configuration defaults to 'none' if no exporter is specified, 
 * keeping your dev logs clean while allowing OTLP in production.
 */
@Configuration
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("OTEL-INIT: Initializing OpenTelemetry SDK...");
        
        // DEFAULT TO 'none' FOR CLEAN LOGS:
        // If the user hasn't specified an exporter, we default to 'none' 
        // to prevent background connection error spam.
        if (System.getProperty("otel.traces.exporter") == null && 
            System.getenv("OTEL_TRACES_EXPORTER") == null) {
            log.info("OTEL-CONFIG: No exporter found. Defaulting to 'none' for clean logs.");
            System.setProperty("otel.traces.exporter", "none");
            System.setProperty("otel.metrics.exporter", "none");
            System.setProperty("otel.logs.exporter", "none");
        }

        try {
            return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
        } catch (Exception e) {
            log.warn("OTEL-INIT-WARN: SDK initialization failed. Falling back to Noop.");
            return OpenTelemetry.noop();
        }
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.velo.sentinel", "0.0.1");
    }
}
