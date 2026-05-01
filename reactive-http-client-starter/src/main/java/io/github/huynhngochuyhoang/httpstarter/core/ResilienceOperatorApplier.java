package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Applies resilience operators to Reactor publishers.
 */
public interface ResilienceOperatorApplier {

    /** Resilience component categories used by {@link #isInstanceConfigured(InstanceType, String)}. */
    enum InstanceType {
        RETRY, CIRCUIT_BREAKER, BULKHEAD
    }

    <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName);

    <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName);

    <T> Mono<T> applyRetry(Mono<T> mono, String instanceName);

    <T> Flux<T> applyRetry(Flux<T> flux, String instanceName);

    <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName);

    <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName);

    /**
     * {@code true} if the named instance is registered in the corresponding
     * Resilience4j registry. Used by the starter at proxy-construction time to
     * fail fast on a typo in a per-method {@code @Retry} / {@code @CircuitBreaker}
     * / {@code @Bulkhead} annotation. Implementations that have no registry (e.g.
     * {@link NoopResilienceOperatorApplier}) must return {@code true} to
     * effectively skip validation.
     */
    default boolean isInstanceConfigured(InstanceType type, String instanceName) {
        return true;
    }
}
