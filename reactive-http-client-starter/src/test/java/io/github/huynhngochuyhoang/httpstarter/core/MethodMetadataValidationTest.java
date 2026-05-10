package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.PATCH;
import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void shouldParseMethodLevelLogHttpExchange() throws Exception {
        Method method = MethodLoggedClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertTrue(metadata.isHttpExchangeLoggingEnabled());
        assertEquals(OverrideTestExchangeLogger.class, metadata.getHttpExchangeLoggerClass());
    }

    @Test
    void shouldNotParseClientLevelLogHttpExchangeIntoSharedMethodMetadata() throws Exception {
        Method method = ClassLevelLoggedClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertFalse(metadata.isHttpExchangeLoggingEnabled());
    }

    @Test
    void shouldParseApiRefAnnotation() throws Exception {
        Method method = ApiRefClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals("user.getById", metadata.getApiRefName());
        assertNull(metadata.getHttpMethod());
    }

    @Test
    void shouldRejectApiRefCombinedWithHttpVerb() throws Exception {
        Method method = InvalidApiRefClient.class.getMethod("call");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("@ApiRef cannot be combined"));
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
    }

    interface MethodLoggedClient {
        @GET("/items/override")
        @LogHttpExchange(logger = OverrideTestExchangeLogger.class)
        Mono<String> call();
    }

    interface ApiRefClient {
        @ApiRef("user.getById")
        Mono<String> call();
    }

    interface InvalidApiRefClient {
        @GET("/items")
        @ApiRef("user.getById")
        Mono<String> call();
    }

    static final class OverrideTestExchangeLogger implements HttpExchangeLogger {
        @Override
        public void log(HttpExchangeLogContext context) {
            // no-op
        }
    }
}
