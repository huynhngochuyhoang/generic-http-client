package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

class ResilienceOperatorApplierTest {

    // -------------------------------------------------------------------------
    // NoopResilienceOperatorApplier
    // -------------------------------------------------------------------------

    @Nested
    class NoopApplier {

        private final ResilienceOperatorApplier applier = new NoopResilienceOperatorApplier();

        @Test
        void circuitBreakerMono_passesThroughValue() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_passesThroughValues() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1, 2, 3), "cb"))
                    .expectNext(1, 2, 3)
                    .verifyComplete();
        }

        @Test
        void retryMono_passesThroughValue() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "retry"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_passesThroughValues() {
            StepVerifier.create(applier.applyRetry(Flux.just("a", "b"), "retry"))
                    .expectNext("a", "b")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_passesThroughValue() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "bh"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_passesThroughValues() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "bh"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerMono_propagatesError() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.error(new RuntimeException("boom")), "cb"))
                    .expectErrorMessage("boom")
                    .verify();
        }

        @Test
        void retryMono_propagatesError() {
            StepVerifier.create(applier.applyRetry(Mono.error(new RuntimeException("fail")), "retry"))
                    .expectErrorMessage("fail")
                    .verify();
        }

        @Test
        void bulkheadMono_propagatesError() {
            StepVerifier.create(applier.applyBulkhead(Mono.error(new RuntimeException("err")), "bh"))
                    .expectErrorMessage("err")
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // Resilience4jOperatorApplier — with registries
    // -------------------------------------------------------------------------

    @Nested
    class Resilience4jApplier {

        private final CircuitBreakerRegistry cbRegistry =
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
        private final RetryRegistry retryRegistry =
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        private final BulkheadRegistry bulkheadRegistry =
                BulkheadRegistry.of(BulkheadConfig.ofDefaults());

        private final ResilienceOperatorApplier applier =
                new Resilience4jOperatorApplier(cbRegistry, retryRegistry, bulkheadRegistry);

        @Test
        void circuitBreakerMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1, 2), "test"))
                    .expectNext(1, 2)
                    .verifyComplete();
        }

        @Test
        void retryMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRetry(Flux.just("a", "b"), "test"))
                    .expectNext("a", "b")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "test"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerMono_propagatesErrorAndRecordsFailure() {
            StepVerifier.create(applier.applyCircuitBreaker(
                            Mono.error(new RuntimeException("downstream")), "test"))
                    .expectErrorMessage("downstream")
                    .verify();
        }

        @Test
        void circuitBreakerFlux_propagatesErrorAndRecordsFailure() {
            StepVerifier.create(applier.applyCircuitBreaker(
                            Flux.error(new RuntimeException("downstream")), "test"))
                    .expectErrorMessage("downstream")
                    .verify();
        }

        @Test
        void bulkheadMono_rejectsWhenFull() {
            BulkheadRegistry saturated = BulkheadRegistry.of(
                    BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
            saturated.bulkhead("full").tryAcquirePermission();

            ResilienceOperatorApplier limited =
                    new Resilience4jOperatorApplier(null, null, saturated);

            StepVerifier.create(limited.applyBulkhead(Mono.just("x"), "full"))
                    .expectError(io.github.resilience4j.bulkhead.BulkheadFullException.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // Resilience4jOperatorApplier — null registries fall through
    // -------------------------------------------------------------------------

    @Nested
    class Resilience4jApplierNullRegistries {

        private final ResilienceOperatorApplier applier =
                new Resilience4jOperatorApplier(null, null, null);

        @Test
        void circuitBreakerMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1), "cb"))
                    .expectNext(1)
                    .verifyComplete();
        }

        @Test
        void retryMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "retry"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRetry(Flux.just("a"), "retry"))
                    .expectNext("a")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "bh"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "bh"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void nonResistance4jObjectsAreIgnored() {
            ResilienceOperatorApplier withGarbage =
                    new Resilience4jOperatorApplier("not-a-registry", 42, new Object());

            StepVerifier.create(withGarbage.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }
    }
}
