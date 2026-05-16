package io.github.huynhngochuyhoang.httpstarter.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a fresh {@link MockReactiveHttpClient} into an annotated test field
 * before each JUnit 5 test method.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockHttpServerExtension.class)
public @interface MockHttpServer {
}
