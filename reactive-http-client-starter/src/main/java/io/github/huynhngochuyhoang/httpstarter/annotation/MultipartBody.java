package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as sending a {@code multipart/form-data} request body. Parts are
 * supplied via parameters annotated with {@link FormField} or {@link FormFile};
 * a single {@link Body} parameter is not allowed on the same method.
 *
 * <p>The starter generates the {@code Content-Type} header with the correct
 * boundary automatically; callers do not need to set it via {@link HeaderParam}.
 *
 * <p>Example:
 * <pre>{@code
 * @POST("/users/{id}/avatar")
 * @MultipartBody
 * Mono<Void> uploadAvatar(
 *         @PathVar("id") long userId,
 *         @FormField("description") String description,
 *         @FormFile("avatar") org.springframework.core.io.Resource avatarFile);
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MultipartBody {
}
