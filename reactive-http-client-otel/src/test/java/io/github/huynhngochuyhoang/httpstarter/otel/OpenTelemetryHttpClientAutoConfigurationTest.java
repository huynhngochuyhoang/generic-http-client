package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryHttpClientAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryHttpClientAutoConfiguration.class))
            .withUserConfiguration(OpenTelemetryConfig.class);

    @Test
    void defaultConfigurationRegistersObserverAndPropagation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HttpClientObserver.class);
            assertThat(context).hasSingleBean(OpenTelemetryContextWebFilter.class);
            assertThat(context).hasBean("openTelemetryContextWebClientCustomizer");
            assertThat(context.getBean("openTelemetryContextWebClientCustomizer"))
                    .isInstanceOf(WebClientCustomizer.class);
        });
    }

    @Test
    void masterSwitchDisablesObserverAndPropagation() {
        runner.withPropertyValues("reactive.http.observability.otel.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void spansCanBeDisabledWhilePropagationStaysEnabled() {
        runner.withPropertyValues("reactive.http.observability.otel.spans.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpClientObserver.class);
                    assertThat(context).hasSingleBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).hasBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void propagationCanBeDisabledWhileObserverStaysEnabled() {
        runner.withPropertyValues("reactive.http.observability.otel.propagation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OpenTelemetryConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }
}
