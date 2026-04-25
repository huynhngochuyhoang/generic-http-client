package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a named scalar part of a {@link MultipartBody} request. The annotated
 * parameter's value is {@code String.valueOf(...)}-stringified and sent as a
 * {@code text/plain} part with {@code Content-Disposition: form-data; name="..."}.
 *
 * <p>Null values are skipped (the part is omitted); {@link java.util.Collection}
 * / array values contribute one part per element, matching the repeat semantics
 * of {@link QueryParam}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormField {
    String value();
}
