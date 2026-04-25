package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.NoopResilienceOperatorApplier;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveClientInvocationHandler;
import io.github.huynhngochuyhoang.httpstarter.core.RequestArgumentResolver;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Builder that produces a proxy for a {@code @ReactiveHttpClient} interface backed
 * by an in-process {@link ExchangeFunction}. Every call through the proxy is
 * materialised into a {@link RecordedExchange}, so tests can assert on request
 * URL, headers, body, and the response that was served.
 *
 * <pre>{@code
 * MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
 *         .baseUrl("http://mock.local")
 *         .respondTo(HttpMethod.POST, "/users", request -> MockReactiveHttpClient.json(201, "{\"id\":1}"))
 *         .build();
 *
 * StepVerifier.create(mock.proxy().createUser(...))
 *         .expectNext(new User(1, ...))
 *         .verifyComplete();
 *
 * assertThat(mock.exchanges()).hasSize(1);
 * assertThat(mock.exchanges().get(0).bodyAsString()).contains("\"name\":\"alice\"");
 * }</pre>
 *
 * <p>Each matcher is consulted in registration order; the first matching handler
 * serves the request. Unmatched requests produce an HTTP 404 response so the
 * test fails loudly instead of hanging.
 */
public final class MockReactiveHttpClient<T> {

    private final T proxy;
    private final List<RecordedExchange> exchanges;

    private MockReactiveHttpClient(T proxy, List<RecordedExchange> exchanges) {
        this.proxy = proxy;
        this.exchanges = exchanges;
    }

    /** Returns the proxy implementing {@code T} — invoke its methods to exercise the client. */
    public T proxy() { return proxy; }

    /** Returns the recorded exchanges in call order. The list is live; it grows as more calls are made. */
    public List<RecordedExchange> exchanges() { return exchanges; }

    /** The most recently recorded exchange, or {@code null} if none. */
    public RecordedExchange lastExchange() {
        return exchanges.isEmpty() ? null : exchanges.get(exchanges.size() - 1);
    }

    /** Convenience factory producing a JSON response. */
    public static ClientResponse json(int status, String body) {
        return ClientResponse.create(HttpStatus.valueOf(status))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    /** Convenience factory producing an empty response (used for Void-returning methods). */
    public static ClientResponse empty(int status) {
        return ClientResponse.create(HttpStatus.valueOf(status)).build();
    }

    public static <T> Builder<T> forClient(Class<T> clientInterface) {
        return new Builder<>(clientInterface);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder<T> {
        private final Class<T> clientInterface;
        private String baseUrl = "http://mock.local";
        private final List<Matcher> matchers = new ArrayList<>();
        private ClientResponse fallback = ClientResponse.create(HttpStatus.NOT_FOUND)
                .body("mock: no matcher for this request")
                .build();

        private Builder(Class<T> clientInterface) {
            this.clientInterface = clientInterface;
        }

        public Builder<T> baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Registers a handler that responds to any request whose URL path equals
         * {@code path}. Shortcut for {@link #respond(java.util.function.Predicate, Function)}
         * with a simple path equality predicate.
         */
        public Builder<T> respondToPath(String path, Function<RecordedExchange, ClientResponse> handler) {
            return respond(ex -> path.equals(ex.uri().getPath()), handler);
        }

        /**
         * Registers a handler that responds to any request whose method and path
         * match.
         */
        public Builder<T> respondTo(org.springframework.http.HttpMethod method,
                                    String path,
                                    Function<RecordedExchange, ClientResponse> handler) {
            return respond(ex -> method.equals(ex.method()) && path.equals(ex.uri().getPath()), handler);
        }

        /** Registers a handler behind an arbitrary predicate. */
        public Builder<T> respond(java.util.function.Predicate<RecordedExchange> predicate,
                                  Function<RecordedExchange, ClientResponse> handler) {
            matchers.add(new Matcher(predicate, handler));
            return this;
        }

        /** Response served when no matcher applies. Defaults to HTTP 404. */
        public Builder<T> fallback(ClientResponse fallback) {
            this.fallback = fallback;
            return this;
        }

        public MockReactiveHttpClient<T> build() {
            List<RecordedExchange> exchanges = new CopyOnWriteArrayList<>();

            ExchangeFunction exchangeFunction = request -> {
                MockClientHttpRequest materialized = new MockClientHttpRequest(
                        request.method(), URI.create(request.url().toString()));
                return request.writeTo(materialized, ExchangeStrategies.withDefaults())
                        .then(Mono.fromCallable(() -> {
                            RecordedExchange exchange = new RecordedExchange(
                                    request.method(),
                                    URI.create(request.url().toString()),
                                    materialized);
                            exchanges.add(exchange);
                            for (Matcher matcher : matchers) {
                                if (matcher.predicate.test(exchange)) {
                                    return matcher.handler.apply(exchange);
                                }
                            }
                            return fallback;
                        }));
            };

            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .exchangeFunction(exchangeFunction)
                    .build();

            // Empty StaticApplicationContext — no HttpClientObserver registered, so
            // observerProvider.getIfAvailable() returns null and the handler skips
            // observer notifications. Sufficient for test scenarios that don't need
            // metrics; users can override by calling withObserver(...) on a future
            // builder method if needed.
            StaticApplicationContext appCtx = new StaticApplicationContext();
            appCtx.refresh();

            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient,
                    new MethodMetadataCache(),
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    new ReactiveHttpClientProperties.ClientConfig(),
                    "mock-client",
                    appCtx,
                    new NoopResilienceOperatorApplier(),
                    null,
                    new ReactiveHttpClientProperties.ObservabilityConfig()
            );

            @SuppressWarnings("unchecked")
            T proxy = (T) Proxy.newProxyInstance(
                    clientInterface.getClassLoader(),
                    new Class<?>[]{clientInterface},
                    handler);

            return new MockReactiveHttpClient<>(proxy, exchanges);
        }
    }

    private record Matcher(java.util.function.Predicate<RecordedExchange> predicate,
                           Function<RecordedExchange, ClientResponse> handler) {
    }
}
