package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * References a named API definition from
 * {@code reactive.http.clients.<client-name>.apis.<api-name>}.
 *
 * <p>When present, request method/path (and optional timeout) are resolved from
 * client configuration instead of method-level verb annotations.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiRef {
    String value();
}
