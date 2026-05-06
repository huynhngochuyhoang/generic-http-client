package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpClientHealthIndicator}. Uses a {@link SimpleMeterRegistry}
 * plus the real {@link MicrometerHttpClientObserver} to seed realistic
 * {@code reactive.http.client.requests} meters, then probes {@code health()} and asserts
 * on the probe-to-probe delta semantics.
 */
class HttpClientHealthIndicatorTest {

    @Test
    void reportsUpAndNoSamplesOnFirstProbeWithEmptyRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpClientHealthIndicator indicator = indicator(registry, defaults());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("minSamples", 10L);
    }

    @Test
    void reportsDownWhenErrorRateExceedsThreshold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        HttpClientHealthIndicator indicator = indicator(registry, config);

        // Seed 3 successes + 7 errors = 70% error rate
        record(registry, config, "failing-client", 3, 7);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(clientDetails(health, "failing-client"))
                .containsEntry("status", "DOWN")
                .containsEntry("samples", 10L)
                .containsEntry("errors", 7L)
                .containsEntry("errorRate", 0.7d);
    }

    @Test
    void reportsUpWhenErrorRateBelowThreshold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "healthy-client", 9, 1); // 10% error rate

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "healthy-client"))
                .containsEntry("status", "UP")
                .containsEntry("samples", 10L)
                .containsEntry("errors", 1L)
                .containsEntry("errorRate", 0.1d);
    }

    @Test
    void insufficientSamplesDoesNotFailHealth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(10);
        HttpClientHealthIndicator indicator = indicator(registry, config);

        // 2 errors out of 2 invocations is 100%, but below minSamples
        record(registry, config, "quiet-client", 0, 2);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "quiet-client"))
                .containsEntry("status", "INSUFFICIENT_SAMPLES")
                .containsEntry("samples", 2L)
                .containsEntry("errors", 2L)
                .doesNotContainKey("errorRate");
    }

    @Test
    void windowIsProbeToProbeDelta() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        HttpClientHealthIndicator indicator = indicator(registry, config);

        // Window 1: 10 successful calls — baseline that should NOT count toward probe #2
        record(registry, config, "flaky-client", 10, 0);
        Health first = indicator.health();
        assertThat(first.getStatus()).isEqualTo(Status.UP);

        // Window 2: 2 successes + 8 errors since last probe — rolling window is 80% errors
        record(registry, config, "flaky-client", 2, 8);
        Health second = indicator.health();

        assertThat(second.getStatus())
                .as("rolling window should consider only calls since the previous probe")
                .isEqualTo(Status.DOWN);
        assertThat(clientDetails(second, "flaky-client"))
                .containsEntry("samples", 10L)
                .containsEntry("errors", 8L)
                .containsEntry("errorRate", 0.8d);
    }

    @Test
    void degradedClientTurnsOverallStatusDownEvenIfOthersAreHealthy() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "healthy-client", 10, 0);
        record(registry, config, "broken-client", 2, 8);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(clientDetails(health, "healthy-client"))
                .containsEntry("status", "UP");
        assertThat(clientDetails(health, "broken-client"))
                .containsEntry("status", "DOWN");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpClientHealthIndicator indicator(
            SimpleMeterRegistry registry,
            ReactiveHttpClientProperties.ObservabilityConfig config) {
        return new HttpClientHealthIndicator(registry, config);
    }

    private static ReactiveHttpClientProperties.ObservabilityConfig defaults() {
        return new ReactiveHttpClientProperties.ObservabilityConfig();
    }

    private static void record(SimpleMeterRegistry registry,
                               ReactiveHttpClientProperties.ObservabilityConfig config,
                               String clientName,
                               int successCount,
                               int errorCount) {
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(registry, config);
        for (int i = 0; i < successCount; i++) {
            observer.record(new HttpClientObserverEvent(
                    clientName, "op", "GET", "/p",
                    200, 1L, null, null, null, null,
                    1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));
        }
        for (int i = 0; i < errorCount; i++) {
            observer.record(new HttpClientObserverEvent(
                    clientName, "op", "GET", "/p",
                    500, 1L, new RuntimeException("boom"), ErrorCategory.SERVER_ERROR, null, null,
                    1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> clientDetails(Health health, String clientName) {
        Object details = health.getDetails().get(clientName);
        assertThat(details)
                .as("expected per-client details for " + clientName)
                .isInstanceOf(java.util.Map.class);
        return (java.util.Map<String, Object>) details;
    }
}
