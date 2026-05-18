package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.Optional;

/**
 * Maps a structured error response body to an application-specific exception.
 */
@FunctionalInterface
public interface ErrorResponseMapper {

    /**
     * Return {@code false} to skip this mapper for a client. Defaults to all clients.
     */
    default boolean supports(String clientName) {
        return true;
    }

    /**
     * Return {@link Optional#empty()} when this mapper does not apply.
     *
     * <p>Throwing from this method is treated as a mapper miss; the default decoder
     * remains the fallback so error handling never masks the upstream status/body.
     */
    Optional<? extends Throwable> map(ErrorResponseContext context) throws Exception;
}
