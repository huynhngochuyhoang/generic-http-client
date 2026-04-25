package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end sanity tests for the test-helper module: verify
 * {@link MockReactiveHttpClient} drives a {@code @ReactiveHttpClient} proxy
 * through canned responses and {@link ErrorCategoryAssertions} interprets
 * library errors.
 */
class MockReactiveHttpClientTest {

    interface SampleClient {
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") long id);

        @POST("/users")
        Mono<String> createUser(@Body String json);
    }

    @Test
    void servesRegisteredResponseAndRecordsTheExchange() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .baseUrl("http://mock.local")
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(mock.exchanges()).hasSize(1);
        RecordedExchange recorded = mock.lastExchange();
        assertThat(recorded.method()).isEqualTo(HttpMethod.GET);
        assertThat(recorded.uri().getPath()).isEqualTo("/users/42");
    }

    @Test
    void capturesPostBodyForAssertion() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.POST, "/users",
                        ex -> MockReactiveHttpClient.json(201, "\"ok\""))
                .build();

        mock.proxy().createUser("{\"name\":\"alice\"}").block();

        assertThat(mock.lastExchange().bodyAsString()).contains("\"name\":\"alice\"");
    }

    @Test
    void unmatchedRequestFallsThroughToFallbackResponse() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class).build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
                .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
                .hasStatusCode(404);

        assertThat(mock.exchanges()).hasSize(1);
    }

    @Test
    void errorCategoryAssertionRecognises429() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/7",
                        ex -> MockReactiveHttpClient.json(429, "{\"reason\":\"slow down\"}"))
                .build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(7))
                .hasStatusCode(429)
                .hasErrorCategory(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void errorCategoryAssertionRecognises5xxAsServerError() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/8",
                        ex -> MockReactiveHttpClient.json(503, "{\"err\":\"down\"}"))
                .build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(8))
                .hasStatusCode(503)
                .hasErrorCategory(ErrorCategory.SERVER_ERROR);
    }
}
