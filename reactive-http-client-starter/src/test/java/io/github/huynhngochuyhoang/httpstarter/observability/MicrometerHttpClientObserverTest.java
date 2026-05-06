package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MicrometerHttpClientObserverTest {

    @Test
    void shouldRecordErrorCategoryTagWhenPresent() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                429,
                12,
                new RuntimeException("rate-limited"),
                ErrorCategory.RATE_LIMITED,
                null,
                null
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("error.category", "RATE_LIMITED")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldRecordNoneErrorCategoryForSuccess() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                200,
                8,
                null,
                null,
                null,
                "ok"
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("error.category", "none")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldUseNoneHttpStatusCodeWhenRequestFailsBeforeResponse() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                null,
                15,
                new RuntimeException("connect failed"),
                ErrorCategory.CONNECT_ERROR,
                null,
                null
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("http.status_code", "NONE")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldDefaultClientNameTagToUnknownWhenNull() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                null,
                "user.get",
                "GET",
                "/users/{id}",
                200,
                8,
                null,
                null,
                null,
                "ok"
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("client.name", "UNKNOWN")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void recordsRequestAndResponseSizeDistributionsWhenSizesAreKnown() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "POST", "/users",
                201, 5, null, null, null, null,
                1, 128L, 256L
        ));

        DistributionSummary requestSize = meterRegistry.find("reactive.http.client.requests.request.size")
                .tag("client.name", "user-service")
                .summary();
        DistributionSummary responseSize = meterRegistry.find("reactive.http.client.requests.response.size")
                .tag("client.name", "user-service")
                .summary();

        assertNotNull(requestSize);
        assertEquals(1, requestSize.count());
        assertEquals(128.0d, requestSize.totalAmount(), 0.0d);

        assertNotNull(responseSize);
        assertEquals(1, responseSize.count());
        assertEquals(256.0d, responseSize.totalAmount(), 0.0d);
    }

    @Test
    void skipsSizeDistributionsWhenSizesAreUnknown() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 5, null, null, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        assertNull(meterRegistry.find("reactive.http.client.requests.request.size").summary(),
                "unknown request size must not create a distribution summary meter");
        assertNull(meterRegistry.find("reactive.http.client.requests.response.size").summary(),
                "unknown response size must not create a distribution summary meter");
    }

    @Test
    void recordsZeroSizedRequestBodyExplicitly() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 5, null, null, null, null,
                1, 0L, 0L
        ));

        DistributionSummary requestSize = meterRegistry.find("reactive.http.client.requests.request.size").summary();
        DistributionSummary responseSize = meterRegistry.find("reactive.http.client.requests.response.size").summary();

        assertNotNull(requestSize);
        assertEquals(1, requestSize.count(),
                "null / empty bodies must still be recorded as 0 bytes, not dropped");
        assertNotNull(responseSize);
        assertEquals(1, responseSize.count());
    }

    // ---- histogram tests ----

    @Test
    void histogramNotRecordedWhenDisabled() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        // histogram.enabled defaults to false

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 50, null, null, null, null
        ));

        assertNull(meterRegistry.find("reactive.http.client.requests.latency").timer(),
                "no latency histogram should be registered when histogram is disabled");
    }

    @Test
    void histogramRecordedWhenEnabled() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 75, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency")
                .tag("client.name", "user-service")
                .tag("api.name", "user.get")
                .tag("http.method", "GET")
                .tag("uri", "/users/{id}")
                .timer();
        assertNotNull(histogramTimer, "latency histogram timer must be registered when enabled");
        assertEquals(1, histogramTimer.count());
    }

    @Test
    void histogramUsesOnlyLowCardinalityTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "POST", "/items",
                500, 120, new RuntimeException("boom"), ErrorCategory.SERVER_ERROR, null, null
        ));

        // Histogram timer must exist with only the 4 low-cardinality tags
        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency")
                .tag("client.name", "svc")
                .tag("api.name", "api.op")
                .tag("http.method", "POST")
                .tag("uri", "/items")
                .timer();
        assertNotNull(histogramTimer, "histogram timer must be present");

        // The histogram timer must NOT carry high-cardinality tags
        Meter.Id id = histogramTimer.getId();
        assertNull(id.getTag("http.status_code"), "histogram must not have http.status_code tag");
        assertNull(id.getTag("outcome"),           "histogram must not have outcome tag");
        assertNull(id.getTag("exception"),         "histogram must not have exception tag");
        assertNull(id.getTag("error.category"),    "histogram must not have error.category tag");
    }

    @Test
    void histogramDefaultSloBoundariesAreUsedWhenNotConfigured() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);
        // slo-boundaries-ms left at default

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/ping",
                200, 50, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency").timer();
        assertNotNull(histogramTimer, "latency histogram must be present");
        // Verify default SLO list is [50, 100, 200, 500, 1000, 2000, 5000]
        List<Long> expected = Arrays.asList(50L, 100L, 200L, 500L, 1000L, 2000L, 5000L);
        assertEquals(expected, config.getHistogram().getSloBoundariesMs());
    }

    @Test
    void histogramUsesCustomSloBoundaries() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);
        config.getHistogram().setSloBoundariesMs(Arrays.asList(10L, 25L, 100L));

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/ping",
                200, 15, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency").timer();
        assertNotNull(histogramTimer, "latency histogram must be present with custom SLO buckets");
        assertEquals(1, histogramTimer.count());
    }

    @Test
    void histogramDoesNotAffectMainTimer() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/health",
                200, 30, null, null, null, null
        ));

        // Main timer must still carry full tag set
        Timer mainTimer = meterRegistry.find("reactive.http.client.requests")
                .tag("http.status_code", "200")
                .tag("outcome", "SUCCESS")
                .tag("exception", "none")
                .tag("error.category", "none")
                .timer();
        assertNotNull(mainTimer, "main timer must still carry high-cardinality tags when histogram is enabled");
        assertEquals(1, mainTimer.count());
    }
}
