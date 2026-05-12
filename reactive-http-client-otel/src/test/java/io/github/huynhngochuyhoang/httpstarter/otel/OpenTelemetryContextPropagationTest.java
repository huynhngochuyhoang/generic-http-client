package io.github.huynhngochuyhoang.httpstarter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the OTel context propagation pipeline (roadmap 1.1 — baggage
 * propagation): inbound headers → Reactor context (via the WebFilter) →
 * outbound headers (via the WebClient ExchangeFilterFunction).
 *
 * <p>Uses a real {@link OpenTelemetrySdk} with both the W3C trace-context
 * and W3C baggage propagators registered, so the {@code traceparent} and
 * {@code baggage} headers are exercised end-to-end.
 */
class OpenTelemetryContextPropagationTest {

    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUp() {
        openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(),
                                W3CBaggagePropagator.getInstance())))
                .build();
    }

    @Test
    void webFilterExtractsBaggageFromInboundHeadersIntoReactorContext() {
        OpenTelemetryContextWebFilter filter = new OpenTelemetryContextWebFilter(openTelemetry);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("baggage", "userId=alice,tenant=acme")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Context> capturedContext = new AtomicReference<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedContext.set(ctx.getOrDefault(
                    OpenTelemetryContextWebFilter.OTEL_CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        Context extracted = capturedContext.get();
        assertThat(extracted)
                .as("WebFilter must place the extracted OTel Context into the Reactor ContextView")
                .isNotNull();
        Baggage baggage = Baggage.fromContext(extracted);
        assertThat(baggage.getEntryValue("userId")).isEqualTo("alice");
        assertThat(baggage.getEntryValue("tenant")).isEqualTo("acme");
    }

    @Test
    void exchangeFilterInjectsBaggageOntoOutboundRequest() {
        ExchangeFilterFunction filter = OpenTelemetryContextExchangeFilter.create(openTelemetry);

        // Build an OTel context carrying baggage and wrap the WebClient call so it sees that
        // context via the Reactor ContextView (matching what the server-side filter does).
        Context contextWithBaggage = Context.root().with(
                Baggage.builder().put("userId", "alice").put("tenant", "acme").build());

        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://otel.test")
                .filter(filter)
                .exchangeFunction(req -> {
                    captured.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();

        Mono<Void> call = webClient.get().uri("/users").retrieve().bodyToMono(Void.class)
                .contextWrite(ctx -> ctx.put(
                        OpenTelemetryContextWebFilter.OTEL_CONTEXT_KEY, contextWithBaggage));

        StepVerifier.create(call).verifyComplete();

        String baggageHeader = captured.get().headers().getFirst("baggage");
        assertThat(baggageHeader)
                .as("outbound request must carry the baggage from the propagated OTel Context")
                .isNotNull();
        assertThat(baggageHeader).contains("userId=alice").contains("tenant=acme");
    }

    @Test
    void exchangeFilterDoesNotOverrideCallerSuppliedHeader() {
        ExchangeFilterFunction filter = OpenTelemetryContextExchangeFilter.create(openTelemetry);

        Context contextWithBaggage = Context.root().with(
                Baggage.builder().put("userId", "alice").build());

        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://otel.test")
                .filter(filter)
                .exchangeFunction(req -> {
                    captured.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();

        // Caller pre-sets a baggage header; propagator must not overwrite it
        Mono<Void> call = webClient.get().uri("/users")
                .header("baggage", "preset=caller")
                .retrieve().bodyToMono(Void.class)
                .contextWrite(ctx -> ctx.put(
                        OpenTelemetryContextWebFilter.OTEL_CONTEXT_KEY, contextWithBaggage));

        StepVerifier.create(call).verifyComplete();

        assertThat(captured.get().headers().getFirst("baggage"))
                .as("caller-supplied baggage must win over propagated baggage")
                .isEqualTo("preset=caller");
    }

    @Test
    void exchangeFilterIsNoOpWhenNoContextAndNoCurrent() {
        ExchangeFilterFunction filter = OpenTelemetryContextExchangeFilter.create(openTelemetry);

        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://otel.test")
                .filter(filter)
                .exchangeFunction(req -> {
                    captured.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();

        StepVerifier.create(webClient.get().uri("/x").retrieve().bodyToMono(Void.class))
                .verifyComplete();

        assertThat(captured.get().headers().getFirst("baggage"))
                .as("no propagation context → no baggage header")
                .isNull();
        assertThat(captured.get().headers().getFirst("traceparent"))
                .as("no propagation context → no traceparent header")
                .isNull();
    }

    @Test
    void endToEndPropagationThroughWebFilterAndExchangeFilter() {
        OpenTelemetryContextWebFilter inboundFilter = new OpenTelemetryContextWebFilter(openTelemetry);
        ExchangeFilterFunction outboundFilter = OpenTelemetryContextExchangeFilter.create(openTelemetry);

        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://otel.test")
                .filter(outboundFilter)
                .exchangeFunction(req -> {
                    captured.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();

        // Inbound request carrying baggage
        MockServerHttpRequest inbound = MockServerHttpRequest.get("/api")
                .header("baggage", "userId=alice")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(inbound);

        // Inside the inbound filter chain we make an outbound call; the chain
        // routes the Reactor Context through the WebClient pipeline naturally.
        WebFilterChain chain = ex -> webClient.get().uri("/users")
                .retrieve().bodyToMono(Void.class).then();

        StepVerifier.create(inboundFilter.filter(exchange, chain)).verifyComplete();

        assertThat(captured.get().headers().getFirst("baggage"))
                .as("baggage extracted by the inbound WebFilter must reach the outbound WebClient request")
                .contains("userId=alice");
    }
}
