package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers an {@link OpenTelemetryHttpClientObserver}
 * when the OpenTelemetry API is on the classpath, an {@link OpenTelemetry}
 * bean is available in the context, and no other {@link HttpClientObserver}
 * has been registered.
 *
 * <p>Activated under property
 * {@code reactive.http.observability.otel.enabled} (default {@code true} when
 * the OTel API is present). Set to {@code false} to disable the observer
 * without removing the dependency.
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(
        prefix = "reactive.http.observability.otel",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OpenTelemetryHttpClientAutoConfiguration {

    @Bean(name = "openTelemetryHttpClientObserver")
    @ConditionalOnMissingBean(HttpClientObserver.class)
    public HttpClientObserver openTelemetryHttpClientObserver(OpenTelemetry openTelemetry) {
        return new OpenTelemetryHttpClientObserver(openTelemetry);
    }
}
