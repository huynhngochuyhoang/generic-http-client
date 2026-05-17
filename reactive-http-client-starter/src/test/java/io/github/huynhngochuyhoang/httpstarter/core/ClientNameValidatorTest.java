package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientNameValidatorTest {

    @Test
    void acceptsDocumentedClientNameCharacters() {
        assertThatNoException().isThrownBy(() ->
                ClientNameValidator.validate("orders.v2_eu-west-1", "@ReactiveHttpClient(name)"));
    }

    @Test
    void rejectsBlankOrUnsafeClientNames() {
        assertThatThrownBy(() -> ClientNameValidator.validate(" ", "@ReactiveHttpClient(name)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> ClientNameValidator.validate("-orders", "@ReactiveHttpClient(name)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ClientNameValidator.ALLOWED_PATTERN_DESCRIPTION);

        assertThatThrownBy(() -> ClientNameValidator.validate("orders/payments", "@ReactiveHttpClient(name)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ClientNameValidator.ALLOWED_PATTERN_DESCRIPTION);
    }

    @Test
    void allowsBlankAnnotationNameWhenBaseUrlIsPresent() {
        assertThatNoException().isThrownBy(() ->
                ClientNameValidator.validateAnnotation("", "http://localhost:8080", "@ReactiveHttpClient"));
    }

    @Test
    void rejectsAnnotationWithoutNameOrBaseUrl() {
        assertThatThrownBy(() -> ClientNameValidator.validateAnnotation("", "", "@ReactiveHttpClient"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must define either a non-blank client name or baseUrl");
    }
}
