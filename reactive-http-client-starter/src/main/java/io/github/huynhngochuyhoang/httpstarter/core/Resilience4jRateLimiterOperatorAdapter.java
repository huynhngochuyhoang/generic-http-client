package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Resilience4jRateLimiterOperatorAdapter implements RateLimiterOperatorAdapter {

    private final RateLimiterRegistry rateLimiterRegistry;

    public Resilience4jRateLimiterOperatorAdapter(Object rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry instanceof RateLimiterRegistry registry ? registry : null;
    }

    @Override
    public <T> Mono<T> apply(Mono<T> mono, String instanceName) {
        if (rateLimiterRegistry == null) {
            return mono;
        }
        return mono.transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter(instanceName)));
    }

    @Override
    public <T> Flux<T> apply(Flux<T> flux, String instanceName) {
        if (rateLimiterRegistry == null) {
            return flux;
        }
        return flux.transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter(instanceName)));
    }

    @Override
    public boolean isInstanceConfigured(String instanceName) {
        return rateLimiterRegistry == null || rateLimiterRegistry.find(instanceName).isPresent();
    }
}
