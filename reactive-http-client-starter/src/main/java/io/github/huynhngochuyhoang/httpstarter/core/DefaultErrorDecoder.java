package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Translates HTTP error responses into domain exceptions.
 * <ul>
 *   <li>4xx → {@link HttpClientException}</li>
 *   <li>5xx → {@link RemoteServiceException}</li>
 * </ul>
 */
public class DefaultErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(DefaultErrorDecoder.class);
    private static final int MAX_ERROR_BODY_BYTES = 4096;

    private final String clientName;
    private final List<ErrorResponseMapper> errorResponseMappers;

    public DefaultErrorDecoder() {
        this(null, List.of());
    }

    public DefaultErrorDecoder(ObjectProvider<ErrorResponseMapper> errorResponseMappers) {
        this(null, resolveMappers(errorResponseMappers));
    }

    public DefaultErrorDecoder(String clientName, List<ErrorResponseMapper> errorResponseMappers) {
        this.clientName = clientName;
        this.errorResponseMappers = errorResponseMappers != null ? List.copyOf(errorResponseMappers) : List.of();
    }

    /**
     * Returns a client-scoped decoder when this is the starter's default decoder.
     * Custom subclasses keep their own implementation unchanged.
     */
    public DefaultErrorDecoder forClient(String clientName) {
        if (getClass() != DefaultErrorDecoder.class) {
            return this;
        }
        return new DefaultErrorDecoder(clientName, errorResponseMappers);
    }

    /**
     * Returns a {@code Mono} that immediately signals an appropriate exception for the
     * given error response, or an empty Mono if the status code is not an error.
     */
    public Mono<? extends Throwable> decode(ClientResponse response) {
        int code = response.statusCode().value();
        RequestContext requestContext = resolveRequestContext(response);
        return readBodyWithCap(response, MAX_ERROR_BODY_BYTES)
                .defaultIfEmpty("")
                .map(body -> mapOrDefault(response, code, body, requestContext));
    }

    private Throwable mapOrDefault(ClientResponse response, int code, String body, RequestContext requestContext) {
        ErrorResponseContext context = new ErrorResponseContext(
                clientName,
                code,
                body,
                resolveResponseHeaders(response),
                requestContext.method(),
                requestContext.url(),
                null);
        for (ErrorResponseMapper mapper : errorResponseMappers) {
            if (!supports(mapper, clientName)) {
                continue;
            }
            try {
                Optional<? extends Throwable> mapped = mapper.map(context);
                if (mapped != null && mapped.isPresent()) {
                    return mapped.get();
                }
            } catch (Exception ex) {
                log.warn("ErrorResponseMapper [{}] failed for client [{}] status [{}] - falling back to default decoder: {}",
                        mapper.getClass().getName(), clientName, code, ex.getMessage());
            }
        }
        return context.defaultException();
    }

    private boolean supports(ErrorResponseMapper mapper, String clientName) {
        try {
            return mapper.supports(clientName);
        } catch (Exception ex) {
            log.warn("ErrorResponseMapper [{}] supports() failed for client [{}] - skipping mapper: {}",
                    mapper.getClass().getName(), clientName, ex.getMessage());
            return false;
        }
    }

    private Mono<String> readBodyWithCap(ClientResponse response, int maxBytes) {
        return response.bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    try {
                        byte[] chunk = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(chunk);
                        return chunk;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .reduce(new ByteArrayOutputStream(maxBytes), (output, chunk) -> {
                    int remaining = maxBytes - output.size();
                    if (remaining > 0) {
                        output.write(chunk, 0, Math.min(remaining, chunk.length));
                    }
                    return output;
                })
                .map(output -> output.toString(StandardCharsets.UTF_8))
                .defaultIfEmpty("");
    }

    private RequestContext resolveRequestContext(ClientResponse response) {
        try {
            var request = response.request();
            if (request == null) {
                return RequestContext.EMPTY;
            }
            String method = request.getMethod() != null ? request.getMethod().name() : null;
            URI uri = request.getURI();
            String url = uri != null ? uri.toString() : null;
            return new RequestContext(method, url);
        } catch (UnsupportedOperationException ignored) {
            return RequestContext.EMPTY;
        }
    }

    private HttpHeaders resolveResponseHeaders(ClientResponse response) {
        try {
            ClientResponse.Headers headers = response.headers();
            return headers != null ? headers.asHttpHeaders() : HttpHeaders.EMPTY;
        } catch (UnsupportedOperationException ignored) {
            return HttpHeaders.EMPTY;
        }
    }

    private record RequestContext(String method, String url) {
        private static final RequestContext EMPTY = new RequestContext(null, null);
    }

    private static List<ErrorResponseMapper> resolveMappers(ObjectProvider<ErrorResponseMapper> provider) {
        if (provider == null) {
            return List.of();
        }
        java.util.stream.Stream<ErrorResponseMapper> stream = provider.orderedStream();
        return stream != null ? stream.toList() : List.of();
    }
}
