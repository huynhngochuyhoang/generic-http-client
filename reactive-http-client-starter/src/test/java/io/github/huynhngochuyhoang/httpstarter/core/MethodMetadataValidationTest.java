package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.PATCH;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodMetadataValidationTest {

    @Test
    void shouldRejectNonReactiveReturnType() throws Exception {
        Method method = InvalidReturnTypeClient.class.getMethod("call");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("must return Mono<T> or Flux<T>"));
    }

    @Test
    void shouldParsePatchAnnotation() throws Exception {
        Method method = PatchClient.class.getMethod("patch");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals("PATCH", metadata.getHttpMethod());
        assertEquals("/items/1", metadata.getPathTemplate());
    }

    @Test
    void shouldFreezeMetadataCollectionsAfterParsing() throws Exception {
        Method method = PatchClient.class.getMethod("patch");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertThrows(UnsupportedOperationException.class, () -> metadata.getPathVars().put(0, "id"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getQueryParams().put(0, "q"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getHeaderParams().put(0, "X-Test"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getHeaderMapParams().add(0));
    }

    @Test
    void shouldInheritLogHttpExchangeFromClientInterface() throws Exception {
        Method method = ClassLevelLoggedClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertTrue(metadata.isHttpExchangeLoggingEnabled());
        assertEquals(DefaultHttpExchangeLogger.class, metadata.getHttpExchangeLoggerClass());
    }

    @Test
    void shouldPreferMethodLevelLogHttpExchangeOverClientLevel() throws Exception {
        Method method = ClassLevelLoggedClient.class.getMethod("callWithOverride");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertTrue(metadata.isHttpExchangeLoggingEnabled());
        assertEquals(CustomTestExchangeLogger.class, metadata.getHttpExchangeLoggerClass());
    }

    interface InvalidReturnTypeClient {
        @GET("/items")
        String call();
    }

    interface PatchClient {
        @PATCH("/items/1")
        Mono<String> patch();
    }

    @LogHttpExchange(logger = DefaultHttpExchangeLogger.class)
    interface ClassLevelLoggedClient {
        @GET("/items")
        Mono<String> call();

        @GET("/items/override")
        @LogHttpExchange(logger = CustomTestExchangeLogger.class)
        Mono<String> callWithOverride();
    }

    static final class CustomTestExchangeLogger implements HttpExchangeLogger {
        @Override
        public void log(HttpExchangeLogContext context) {
            // no-op
        }
    }
}
