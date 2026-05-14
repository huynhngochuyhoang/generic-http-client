package io.github.huynhngochuyhoang.httpstarter.observability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeHttpClientObserverTest {

    @Test
    void forwardsToAllObserversAndIsolatesFailures() {
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();
        HttpClientObserver failing = event -> {
            firstCount.incrementAndGet();
            throw new IllegalStateException("metric backend down");
        };
        HttpClientObserver succeeding = event -> secondCount.incrementAndGet();
        CompositeHttpClientObserver composite = new CompositeHttpClientObserver(List.of(failing, succeeding));

        composite.record(new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 200, 1L, null, null, null, null
        ));

        assertEquals(1, firstCount.get());
        assertEquals(1, secondCount.get());
    }
}
