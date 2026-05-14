package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

interface RateLimiterOperatorAdapter {

    <T> Mono<T> apply(Mono<T> mono, String instanceName);

    <T> Flux<T> apply(Flux<T> flux, String instanceName);

    boolean isInstanceConfigured(String instanceName);
}
