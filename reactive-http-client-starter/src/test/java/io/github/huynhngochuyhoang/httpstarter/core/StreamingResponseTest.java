package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the streaming-passthrough patterns from roadmap 1.8: a response larger
 * than the configured {@code codec-max-in-memory-size-mb} is delivered through
 * {@code Flux<DataBuffer>} (and {@code Mono<ResponseEntity<Flux<DataBuffer>>>})
 * without triggering {@code DataBufferLimitException}.
 */
class StreamingResponseTest {

    private static final int CODEC_LIMIT_MB = 1;
    private static final int CHUNK_SIZE = 64 * 1024; // 64 KiB
    private static final int CHUNK_COUNT = 32; // 32 * 64 KiB = 2 MiB → above the 1 MiB codec cap

    @Test
    void fluxOfDataBufferReceivesPayloadLargerThanCodecLimit() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(CODEC_LIMIT_MB * 1024 * 1024))
                .exchangeFunction(req -> Mono.just(largeChunkedResponse()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<Long> totalBytes = invokeStream(handler)
                .map(DataBuffer::readableByteCount)
                .reduce(0L, (acc, sz) -> acc + sz);

        StepVerifier.create(totalBytes)
                .expectNext((long) CHUNK_COUNT * CHUNK_SIZE)
                .verifyComplete();
    }

    @Test
    void monoResponseEntityFluxDataBufferStreamsPayloadAndExposesStatusHeaders() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(CODEC_LIMIT_MB * 1024 * 1024))
                .exchangeFunction(req -> Mono.just(largeChunkedResponse()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<ResponseEntity<Flux<DataBuffer>>> entityMono = invokeStreamEntity(handler);

        StepVerifier.create(entityMono.flatMap(entity -> {
                    assertThat(entity.getStatusCode().value()).isEqualTo(200);
                    assertThat(entity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/octet-stream");
                    return entity.getBody()
                            .map(DataBuffer::readableByteCount)
                            .reduce(0L, (acc, sz) -> acc + sz);
                }))
                .expectNext((long) CHUNK_COUNT * CHUNK_SIZE)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ClientResponse largeChunkedResponse() {
        NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        Flux<DataBuffer> chunks = Flux.range(0, CHUNK_COUNT)
                .map(i -> bufferFactory.wrap(new byte[CHUNK_SIZE]));
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(chunks)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setCodecMaxInMemorySizeMb(CODEC_LIMIT_MB);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "stream-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Flux<DataBuffer> invokeStream(ReactiveClientInvocationHandler handler) {
        try {
            Method m = StreamingClient.class.getMethod("download");
            return (Flux<DataBuffer>) handler.invoke(null, m, new Object[0]);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<ResponseEntity<Flux<DataBuffer>>> invokeStreamEntity(ReactiveClientInvocationHandler handler) {
        try {
            Method m = StreamingClient.class.getMethod("downloadEntity");
            return (Mono<ResponseEntity<Flux<DataBuffer>>>) handler.invoke(null, m, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface StreamingClient {
        @GET("/large-file")
        Flux<DataBuffer> download();

        @GET("/large-file")
        Mono<ResponseEntity<Flux<DataBuffer>>> downloadEntity();
    }
}
