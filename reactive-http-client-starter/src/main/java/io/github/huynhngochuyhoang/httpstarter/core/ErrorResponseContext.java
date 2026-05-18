package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.springframework.http.HttpHeaders;

/**
 * Immutable input passed to {@link ErrorResponseMapper} implementations.
 */
public record ErrorResponseContext(
        String clientName,
        int statusCode,
        String responseBody,
        HttpHeaders responseHeaders,
        String requestMethod,
        String requestUrl,
        ErrorCategory errorCategory
) {

    public ErrorResponseContext {
        responseHeaders = responseHeaders != null
                ? HttpHeaders.readOnlyHttpHeaders(responseHeaders)
                : HttpHeaders.EMPTY;
        errorCategory = errorCategory != null
                ? errorCategory
                : ErrorCategories.fromStatusCode(statusCode);
    }

    /**
     * Builds the starter's default domain exception for this response.
     */
    public RuntimeException defaultException() {
        if (statusCode >= 400 && statusCode < 500) {
            return new HttpClientException(statusCode, responseBody, requestMethod, requestUrl);
        }
        return new RemoteServiceException(statusCode, responseBody, requestMethod, requestUrl);
    }
}
