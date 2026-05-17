package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DefaultHttpExchangeLoggerTest {

    private final DefaultHttpExchangeLogger logger = new DefaultHttpExchangeLogger();

    @Test
    void metadataOnlyPresetOmitsHeadersAndBodies(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY));

        assertThat(output).contains("reqHeaders={}");
        assertThat(output).contains("respHeaders={}");
        assertThat(output).contains("reqBody=[OMITTED]");
        assertThat(output).contains("respBody=[OMITTED]");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("request-body");
    }

    @Test
    void headersPresetLogsRedactedHeadersButOmitsBodies(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.HEADERS));

        assertThat(output).contains("X-Request=visible");
        assertThat(output).contains("Authorization=[REDACTED]");
        assertThat(output).contains("Set-Cookie=[[REDACTED]]");
        assertThat(output).contains("reqBody=[OMITTED]");
        assertThat(output).contains("respBody=[OMITTED]");
        assertThat(output).doesNotContain("secret-token");
    }

    @Test
    void bodiesPresetLogsBodiesAndKeepsHeaderRedaction(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.BODIES));

        assertThat(output).contains("reqBody=request-body");
        assertThat(output).contains("respBody=response-body");
        assertThat(output).contains("Authorization=[REDACTED]");
        assertThat(output).doesNotContain("secret-token");
    }

    private static HttpExchangeLogContext context(ReactiveHttpClientProperties.LogPreset preset) {
        return new HttpExchangeLogContext(
                "orders",
                "GET",
                "/orders/{id}",
                Map.of("id", "42"),
                Map.of("expand", List.of("summary")),
                Map.of("Inbound", List.of("inbound")),
                Map.of("Authorization", "secret-token", "X-Request", "visible"),
                "request-body",
                200,
                Map.of("Set-Cookie", List.of("session=secret"), "X-Response", List.of("visible")),
                "response-body",
                10,
                null,
                preset);
    }
}
