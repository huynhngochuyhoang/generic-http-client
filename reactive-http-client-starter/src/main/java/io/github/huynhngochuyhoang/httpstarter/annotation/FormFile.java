package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a named file part of a {@link MultipartBody} request.
 *
 * <p>The annotated parameter must be one of:
 * <ul>
 *   <li>{@code byte[]} — binary payload</li>
 *   <li>{@link org.springframework.core.io.Resource} — file-backed or in-memory</li>
 *   <li>{@link io.github.huynhngochuyhoang.httpstarter.core.FileAttachment}
 *       — convenience record carrying bytes + filename + content-type</li>
 * </ul>
 *
 * <p>{@link #filename()} and {@link #contentType()} set defaults applied when the
 * parameter value does not carry its own (e.g. raw {@code byte[]}); when a
 * {@code Resource} or {@code FileAttachment} provides the corresponding
 * attribute, the parameter value wins.
 *
 * <p>Null parameter values are skipped.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormFile {
    /** Form field name ({@code Content-Disposition: name="..."}). */
    String value();

    /** Default filename used when the parameter value does not supply one. */
    String filename() default "";

    /** Default MIME type used when the parameter value does not supply one. */
    String contentType() default "application/octet-stream";
}
