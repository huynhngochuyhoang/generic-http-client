package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level override that selects a specific Resilience4j {@code Retry} instance
 * by name, taking precedence over the client-level
 * {@code reactive.http.clients.<name>.resilience.retry} setting.
 *
 * <p>The named instance must be configured in the application's
 * {@code resilience4j.retry.instances} block; otherwise proxy construction fails
 * fast with an {@link IllegalStateException}.
 *
 * <p>Only effective when the client has {@code resilience.enabled = true}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    String value();
}
