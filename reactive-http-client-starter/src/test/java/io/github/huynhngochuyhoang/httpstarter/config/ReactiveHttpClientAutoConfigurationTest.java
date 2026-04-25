package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link ReactiveHttpClientAutoConfiguration}'s prototype-scoped
 * {@code starterWebClientBuilder} — specifically that registered
 * {@link WebClientCustomizer} beans are applied to every builder instance
 * (roadmap item 3.9), and that the prototype scope introduced in 1.8.1 still produces
 * distinct builder instances per pull (no filter state sharing).
 */
class ReactiveHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void starterBuilderAppliesRegisteredCustomizer() {
        runner.withUserConfiguration(CountingCustomizerConfig.class)
                .run(context -> {
                    CountingCustomizer customizer = context.getBean(CountingCustomizer.class);

                    WebClient.Builder builder = context.getBean(WebClient.Builder.class);

                    assertThat(builder).isNotNull();
                    assertThat(customizer.invocationCount()).isEqualTo(1);
                    assertThat(customizer.customizedBuilders()).containsExactly(builder);
                });
    }

    @Test
    void customizerAppliedExactlyOncePerBuilderInstance() {
        runner.withUserConfiguration(CountingCustomizerConfig.class)
                .run(context -> {
                    CountingCustomizer customizer = context.getBean(CountingCustomizer.class);

                    WebClient.Builder first = context.getBean(WebClient.Builder.class);
                    WebClient.Builder second = context.getBean(WebClient.Builder.class);
                    WebClient.Builder third = context.getBean(WebClient.Builder.class);

                    assertThat(customizer.invocationCount())
                            .as("customizer must fire once per builder pulled from the prototype bean")
                            .isEqualTo(3);
                    assertThat(customizer.customizedBuilders())
                            .containsExactly(first, second, third);
                });
    }

    @Test
    void starterBuilderIsPrototypeScopedSoStateIsNotSharedAcrossClients() {
        runner.run(context -> {
            WebClient.Builder first = context.getBean(WebClient.Builder.class);
            WebClient.Builder second = context.getBean(WebClient.Builder.class);

            assertThat(first)
                    .as("prototype scope must hand out a distinct builder per pull — "
                            + "shared instance is the 1.8.1 auth-leak regression")
                    .isNotSameAs(second);
        });
    }

    @Test
    void customizersAppliedInOrder() {
        runner.withUserConfiguration(OrderedCustomizersConfig.class)
                .run(context -> {
                    OrderRecorder recorder = context.getBean(OrderRecorder.class);

                    context.getBean(WebClient.Builder.class);

                    assertThat(recorder.order)
                            .as("WebClientCustomizer beans must be applied in @Order sequence")
                            .containsExactly("first", "second", "third");
                });
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class CountingCustomizerConfig {
        @Bean
        CountingCustomizer countingCustomizer() {
            return new CountingCustomizer();
        }
    }

    static class CountingCustomizer implements WebClientCustomizer {
        private final AtomicInteger count = new AtomicInteger();
        private final List<WebClient.Builder> seen = new ArrayList<>();

        @Override
        public synchronized void customize(WebClient.Builder builder) {
            count.incrementAndGet();
            seen.add(builder);
        }

        int invocationCount() {
            return count.get();
        }

        synchronized List<WebClient.Builder> customizedBuilders() {
            return List.copyOf(seen);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedCustomizersConfig {
        @Bean
        OrderRecorder orderRecorder() {
            return new OrderRecorder();
        }

        @Bean
        @Order(1)
        WebClientCustomizer firstCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("first");
        }

        @Bean
        @Order(2)
        WebClientCustomizer secondCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("second");
        }

        @Bean
        @Order(3)
        WebClientCustomizer thirdCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("third");
        }
    }

    static class OrderRecorder {
        final List<String> order = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Resilience4j Micrometer binding tests (roadmap 2.1b)
    // -------------------------------------------------------------------------

    @Test
    void resilience4jMeterBindersRegisteredWhenRegistriesArePresent() {
        runner.withUserConfiguration(Resilience4jRegistriesConfig.class, SimpleMeterRegistryConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).containsKeys(
                            "reactiveHttpCircuitBreakerMeterBinder",
                            "reactiveHttpRetryMeterBinder",
                            "reactiveHttpBulkheadMeterBinder");
                    assertThat(binders.get("reactiveHttpCircuitBreakerMeterBinder"))
                            .isInstanceOf(TaggedCircuitBreakerMetrics.class);
                    assertThat(binders.get("reactiveHttpRetryMeterBinder"))
                            .isInstanceOf(TaggedRetryMetrics.class);
                    assertThat(binders.get("reactiveHttpBulkheadMeterBinder"))
                            .isInstanceOf(TaggedBulkheadMetrics.class);
                });
    }

    @Test
    void resilience4jBindersSkippedWhenRegistryBeansMissing() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).doesNotContainKeys(
                            "reactiveHttpCircuitBreakerMeterBinder",
                            "reactiveHttpRetryMeterBinder",
                            "reactiveHttpBulkheadMeterBinder");
                });
    }

    @Test
    void resilience4jBindersSkippedWhenMeterRegistryMissing() {
        runner.withUserConfiguration(Resilience4jRegistriesConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Resilience4jRegistriesConfig {
        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        RetryRegistry retryRegistry() {
            return RetryRegistry.ofDefaults();
        }

        @Bean
        BulkheadRegistry bulkheadRegistry() {
            return BulkheadRegistry.ofDefaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SimpleMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
