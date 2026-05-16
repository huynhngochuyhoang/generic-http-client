package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class MockHttpServerExtensionTest {

    @MockHttpServer
    private MockReactiveHttpClient<SampleClient> mock;

    @Test
    void injectsMockAndSupportsMatcherRegistration() {
        mock.respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "alice"));

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(mock.lastExchange().uri().getPath()).isEqualTo("/users/42");
    }

    @Test
    void injectsFreshMockForEachTestMethod() {
        assertThat(mock.exchanges()).isEmpty();
    }

    interface SampleClient {
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") long id);
    }
}
