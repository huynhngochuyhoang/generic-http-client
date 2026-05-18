package io.github.huynhngochuyhoang.httpstarter.test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Immutable record of one outbound {@link org.springframework.web.reactive.function.client.ClientRequest}
 * captured by {@link MockReactiveHttpClient}.
 *
 * <p>The request body is fully materialised via Spring's
 * {@link org.springframework.web.reactive.function.client.ExchangeStrategies},
 * so assertions can inspect the final on-wire form (including multipart boundaries
 * and form-urlencoded encodings) rather than the unresolved
 * {@link org.springframework.web.reactive.function.BodyInserter}.
 */
public final class RecordedExchange {

    private final HttpMethod method;
    private final URI uri;
    private final MockClientHttpRequest materialized;
    private final HttpStatusCode statusCode;
    private final String bodyAsString;

    RecordedExchange(HttpMethod method, URI uri, MockClientHttpRequest materialized) {
        this(method, uri, materialized, null);
    }

    RecordedExchange(HttpMethod method, URI uri, MockClientHttpRequest materialized, HttpStatusCode statusCode) {
        this.method = method;
        this.uri = uri;
        this.materialized = materialized;
        this.statusCode = statusCode;
        this.bodyAsString = readBodyAsString(materialized);
    }

    public HttpMethod method() { return method; }
    public URI uri() { return uri; }

    /** Returns the materialised mock request for low-level header/body inspection. */
    public MockClientHttpRequest materialized() { return materialized; }

    public org.springframework.http.HttpHeaders headers() { return materialized.getHeaders(); }
    public MediaType contentType() { return materialized.getHeaders().getContentType(); }

    /** Returns the first value of {@code headerName}, or {@code null} if absent. */
    public String header(String headerName) { return materialized.getHeaders().getFirst(headerName); }

    /** HTTP status selected by the mock response handler. */
    public HttpStatusCode statusCode() {
        if (statusCode == null) {
            throw new IllegalStateException("No response status has been selected for this exchange yet.");
        }
        return statusCode;
    }

    /** Numeric HTTP status selected by the mock response handler. */
    public int statusCodeValue() { return statusCode().value(); }

    /** UTF-8 decoded request body. Empty string if no body was written. */
    public String bodyAsString() { return bodyAsString; }

    private static String readBodyAsString(MockClientHttpRequest request) {
        return Flux.from(request.getBody())
                .map(buf -> buf.toString(StandardCharsets.UTF_8))
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .defaultIfEmpty("")
                .block();
    }

    @Override
    public String toString() {
        return "RecordedExchange{" + method + " " + uri + ", status="
                + (statusCode == null ? "<pending>" : statusCode) + ", contentType=" + contentType() + "}";
    }
}
