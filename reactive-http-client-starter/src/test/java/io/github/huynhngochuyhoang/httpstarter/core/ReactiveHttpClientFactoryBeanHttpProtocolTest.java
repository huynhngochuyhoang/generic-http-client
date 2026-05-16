package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReactiveHttpClientFactoryBeanHttpProtocolTest {

    @Test
    void leavesHttpClientUnchangedWhenHttp2IsDisabled() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        HttpClient original = HttpClient.create();

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(original, config);

        assertSame(original, configured);
    }

    @Test
    void appliesHttp2WhenClientOptsInWithoutBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(HttpClient.create(), config);

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2);
    }

    @Test
    void appliesTlsHttp2ForHttpsBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://example.com");
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(HttpClient.create(), config);

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2);
    }

    @Test
    void appliesClearTextHttp2ForHttpBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://example.com");
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(HttpClient.create(), config);

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2C);
    }
}
