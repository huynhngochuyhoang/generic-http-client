package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveHttpClientLifecycleHookTest {

    @Test
    void shouldRunMultipleHooksInOrderAndIsolateHookFailures() throws Throwable {
        List<String> events = new ArrayList<>();
        ReactiveHttpClientLifecycleHook first = new RecordingHook("first", events);
        ReactiveHttpClientLifecycleHook failing = new FailingStartHook(events);
        ReactiveHttpClientLifecycleHook second = new RecordingHook("second", events);
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(first, failing, second),
                new NoopResilienceOperatorApplier(), defaultConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(List.of(
                "first:start:1:get",
                "failing:start",
                "second:start:1:get",
                "first:success:1:200",
                "second:success:1:200"), events);
    }

    @Test
    void shouldNotifyErrorHookWithDecodedException() throws Throwable {
        List<ReactiveHttpClientLifecycleContext> errors = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onError(ReactiveHttpClientLifecycleContext context) {
                errors.add(context);
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("bad request")
                        .build()))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(hook),
                new NoopResilienceOperatorApplier(), defaultConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(HttpClientException.class)
                .verify();

        assertEquals(1, errors.size());
        ReactiveHttpClientLifecycleContext context = errors.get(0);
        assertEquals("test-client", context.clientName());
        assertEquals("get", context.apiName());
        assertEquals(400, context.statusCode());
        assertInstanceOf(HttpClientException.class, context.error());
    }

    @Test
    void shouldNotifyRetryAttemptBoundary() throws Throwable {
        List<String> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (calls.incrementAndGet() == 1) {
                        return Mono.error(new IllegalStateException("first attempt failed"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                retryOnceApplier(), retryConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(List.of(
                "hook:start:1:get",
                "hook:retry:2:get",
                "hook:success:2:200"), events);
    }

    @Test
    void shouldNotifyCancellationHook() throws Throwable {
        List<String> events = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.never())
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                new NoopResilienceOperatorApplier(), defaultConfig());
        Method method = StreamingLifecycleClient.class.getMethod("stream");

        @SuppressWarnings("unchecked")
        Flux<String> flux = (Flux<String>) handler.invoke(null, method, new Object[0]);
        StepVerifier.create(flux)
                .thenCancel()
                .verify();

        assertEquals(List.of(
                "hook:start:1:stream",
                "hook:cancel:1"), events);
    }

    @Test
    void shouldPreserveNullQueryElementsWhenLifecycleHookIsRegistered() throws Throwable {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        List<ReactiveHttpClientLifecycleContext> starts = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onStart(ReactiveHttpClientLifecycleContext context) {
                starts.add(context);
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(hook),
                new NoopResilienceOperatorApplier(), defaultConfig());
        Method method = QueryLifecycleClient.class.getMethod("search", List.class);

        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) handler.invoke(null, method, new Object[]{Arrays.asList("a", null, "b")});
        StepVerifier.create(mono)
                .expectNext("ok")
                .verifyComplete();

        assertEquals(Arrays.asList("a", null, "b"), starts.get(0).queryParams().get("tag"));
        assertEquals(List.of("a", "null", "b"),
                UriComponentsBuilder.fromUri(capturedRequest.get().url()).build().getQueryParams().get("tag"));
    }

    private static Mono<String> invokeGet(ReactiveClientInvocationHandler handler, String id) throws Throwable {
        Method method = LifecycleClient.class.getMethod("get", String.class);
        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) handler.invoke(null, method, new Object[]{id});
        return mono;
    }

    private static WebClient okWebClient() {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            List<ReactiveHttpClientLifecycleHook> hooks,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientProperties.ClientConfig config) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        ObjectProvider<ReactiveHttpClientLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(hookProvider);
        when(hookProvider.orderedStream()).thenAnswer(invocation -> hooks.stream());

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                resilienceOperatorApplier,
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static ReactiveHttpClientProperties.ClientConfig defaultConfig() {
        return new ReactiveHttpClientProperties.ClientConfig();
    }

    private static ReactiveHttpClientProperties.ClientConfig retryConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry("retry-test");
        resilience.setRetryMethods(Set.of("GET"));
        config.setResilience(resilience);
        return config;
    }

    private static ResilienceOperatorApplier retryOnceApplier() {
        return new NoopResilienceOperatorApplier() {
            @Override
            public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
                return mono.retry(1);
            }
        };
    }

    interface LifecycleClient {
        @GET("/items/{id}")
        Mono<String> get(@PathVar("id") String id);
    }

    interface StreamingLifecycleClient {
        @GET("/stream")
        Flux<String> stream();
    }

    interface QueryLifecycleClient {
        @GET("/search")
        Mono<String> search(@QueryParam("tag") List<String> tags);
    }

    static final class RecordingHook implements ReactiveHttpClientLifecycleHook {
        private final String name;
        private final List<String> events;

        RecordingHook(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":start:" + context.attemptNumber() + ":" + context.apiName());
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":retry:" + context.attemptNumber() + ":" + context.apiName());
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":success:" + context.attemptNumber() + ":" + context.statusCode());
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":cancel:" + context.attemptNumber());
        }
    }

    static final class FailingStartHook implements ReactiveHttpClientLifecycleHook {
        private final List<String> events;

        FailingStartHook(List<String> events) {
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add("failing:start");
            throw new IllegalStateException("hook failed");
        }
    }
}
