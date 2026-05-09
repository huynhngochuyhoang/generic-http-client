package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenTelemetryHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryHttpClientAutoConfiguration.class));

    @Test
    void createsObserverEvenWhenOpenTelemetryBeanIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(HttpClientObserver.class);
            assertThat(context).hasBean("openTelemetryHttpClientObserver");
        });
    }

    @Test
    void usesApplicationProvidedOpenTelemetryBeanWhenAvailable() {
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        Tracer tracer = mock(Tracer.class);
        when(openTelemetry.getTracer(OpenTelemetryHttpClientObserver.INSTRUMENTATION_NAME)).thenReturn(tracer);

        contextRunner
                .withBean(OpenTelemetry.class, () -> openTelemetry)
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpClientObserver.class);
                    verify(openTelemetry).getTracer(OpenTelemetryHttpClientObserver.INSTRUMENTATION_NAME);
                });
    }

    @Test
    void backsOffWhenCustomHttpClientObserverBeanExists() {
        contextRunner
                .withBean(HttpClientObserver.class, () -> event -> {
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean("openTelemetryHttpClientObserver");
                });
    }

    @Test
    void disabledPropertyPreventsObserverRegistration() {
        contextRunner
                .withPropertyValues("reactive.http.observability.otel.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(HttpClientObserver.class));
    }
}
