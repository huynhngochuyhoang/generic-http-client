package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

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
}
