package io.github.huynhngochuyhoang.httpstarter.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoriesTest {

    @Test
    void extractsCategoryFromStarterExceptions() {
        assertThat(ErrorCategories.from(new HttpClientException(429, "")))
                .isEqualTo(ErrorCategory.RATE_LIMITED);
        assertThat(ErrorCategories.from(new HttpClientException(400, "")))
                .isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ErrorCategories.from(new RemoteServiceException(503, "")))
                .isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test
    void extractsCategoryFromWrappedInfrastructureErrors() {
        assertThat(ErrorCategories.from(new RuntimeException(new TimeoutException())))
                .isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(ErrorCategories.from(new RuntimeException(ReadTimeoutException.INSTANCE)))
                .isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(ErrorCategories.from(new RuntimeException(new CancellationException())))
                .isEqualTo(ErrorCategory.CANCELLED);
        assertThat(ErrorCategories.from(new RuntimeException(new AuthProviderException("test-client", "boom"))))
                .isEqualTo(ErrorCategory.AUTH_PROVIDER_ERROR);
        assertThat(ErrorCategories.from(new RuntimeException(new UnknownHostException("missing.local"))))
                .isEqualTo(ErrorCategory.UNKNOWN_HOST);
        assertThat(ErrorCategories.from(new RuntimeException(new ConnectException("refused"))))
                .isEqualTo(ErrorCategory.CONNECT_ERROR);
    }

    @Test
    void usesStatusCodeWhenThrowableDoesNotCarryCategory() {
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 404))
                .isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 429))
                .isEqualTo(ErrorCategory.RATE_LIMITED);
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 500))
                .isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test
    void reportsDecodeErrorForSuccessfulResponseDecodeFailure() {
        assertThat(ErrorCategories.from(new RuntimeException(new DecodingException("bad json")), 200))
                .isEqualTo(ErrorCategory.RESPONSE_DECODE_ERROR);
    }

    @Test
    void exposesStatusOnlyAndPredicateHelpers() {
        assertThat(ErrorCategories.fromStatusCode(503)).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(ErrorCategories.fromStatusCode(204)).isNull();
        assertThat(ErrorCategories.is(new HttpClientException(429, ""), ErrorCategory.RATE_LIMITED)).isTrue();
    }

    @Test
    void returnsUnknownForUnclassifiedErrorsAndNullForNoError() {
        assertThat(ErrorCategories.from(new RuntimeException("boom"))).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ErrorCategories.from(null)).isNull();
        assertThat(ErrorCategories.from(null, null)).isNull();
    }
}
