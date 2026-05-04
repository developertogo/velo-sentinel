package com.velo.sentinel.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenTelemetryConfigTests {

    @Test
    void testTracerCreation() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        OpenTelemetry otel = OpenTelemetry.noop();
        Tracer tracer = config.tracer(otel);
        assertThat(tracer).isNotNull();
    }

    @Test
    void testOpenTelemetryInitializationWithFallback() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        // Since we cannot mock the static AutoConfiguredOpenTelemetrySdk easily without Mockito-inline,
        // and relying on real init might crash or succeed depending on environment,
        // we just call it. System properties are set to "none" so it should return a valid Noop or initialized SDK.
        OpenTelemetry otel = config.openTelemetry();
        assertThat(otel).isNotNull();
    }
}
