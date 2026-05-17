package io.github.huynhngochuyhoang.httpstarter.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ReactiveHttpClientFactoryBeanDiagnosticsTest {

    @Test
    void debugStartupSummaryIncludesResolvedClientConfigurationAndRedactsSecrets(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setHttp2Enabled(true);
        config.setLogExchange(true);
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = new ReactiveHttpClientProperties.ConnectionPoolConfig();
        pool.setMaxConnections(42);
        pool.setPendingAcquireTimeoutMs(1234);
        config.setPool(pool);
        ReactiveHttpClientProperties.ProxyConfig proxy = new ReactiveHttpClientProperties.ProxyConfig();
        proxy.setHost("proxy.example");
        proxy.setPort(3128);
        proxy.setUsername("proxy-user");
        proxy.setPassword("proxy-secret");
        config.setProxy(proxy);
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setInsecureTrustAll(true);
        config.setTls(tls);
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("diagnostic-retry");
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();
            assertThat(output.getOut())
                    .contains("Reactive HTTP client [diagnostic-client] startup configuration")
                    .contains("source=property")
                    .contains("protocol=HTTP/2")
                    .contains("poolSource=client")
                    .contains("proxy=enabled")
                    .contains("credentials=[REDACTED]")
                    .contains("tls=custom")
                    .contains("resilience=enabled")
                    .contains("exchangeLogging=enabled")
                    .doesNotContain("proxy-secret");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void failsFastWhenProxyPortIsSetWithoutHost() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ProxyConfig proxy = new ReactiveHttpClientProperties.ProxyConfig();
        proxy.setPort(3128);
        config.setProxy(proxy);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proxy port is set but host is blank");
    }

    @Test
    void failsFastWhenTlsTrustStorePasswordIsSetWithoutTrustStore() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setTrustStorePassword("secret");
        config.setTls(tls);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trust-store-password is set but trust-store is blank")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret"));
    }

    @Test
    void failsFastWhenConfiguredAuthProviderBeanIsMissing() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setAuthProvider("missingAuthProvider");
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No AuthProvider bean named 'missingAuthProvider'")
                .hasMessageContaining("diagnostic-client");
    }

    @Test
    void failsFastWhenDefaultHeaderNameIsInvalid() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("Bad Header", "value"));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid header name");
    }

    @Test
    void failsFastWhenDefaultHeaderValueIsNull() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("X-Tenant", null);
        config.setDefaultHeaders(defaultHeaders);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default header 'X-Tenant' for client 'diagnostic-client' must not be null");
    }

    @Test
    void failsFastWhenDefaultHeaderValueContainsControlCharacter() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("X-Tenant", "public\nadmin"));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid header value for 'X-Tenant'");
    }

    @Test
    void warnsWhenDefaultHeaderNameLooksSensitive(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret"));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("default header [Authorization] looks sensitive")
                    .doesNotContain("Bearer secret");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void failsFastWhenDefaultQueryParamNameIsBlank() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("", List.of("en-US")));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default query parameter name must not be blank");
    }

    @Test
    void failsFastWhenDefaultQueryParamValueContainsControlCharacter() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("locale", List.of("en\nUS")));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid default query parameter value for 'locale'");
    }

    @Test
    void warnsWhenDefaultQueryParamNameLooksSensitive(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("api_key", List.of("secret")));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("default query parameter [api_key] looks sensitive")
                    .doesNotContain("api_key=secret");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void authProviderBeanNameTakesPrecedenceOverObjectAuth(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setAuthProvider("namedAuthProvider");
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        config.setAuth(auth);
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("has both auth-provider and auth.type configured")
                    .contains("Using auth-provider bean [namedAuthProvider]")
                    .contains("ignoring object-style auth [oauth2-client-credentials]");
        } finally {
            factoryBean.destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private ReactiveHttpClientFactoryBean<DiagnosticClient> buildFactoryBean(ReactiveHttpClientProperties properties) {
        ApplicationContext ctx = mock(ApplicationContext.class);

        ObjectProvider<Object> defaultProvider = mock(ObjectProvider.class);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        lenient().when(defaultProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());
        lenient().when(defaultProvider.orderedStream()).thenReturn(Stream.empty());
        when(ctx.getBeanProvider(any(Class.class))).thenReturn((ObjectProvider) defaultProvider);

        ObjectProvider<ReactiveHttpClientProperties> propsProvider = mock(ObjectProvider.class);
        when(propsProvider.getIfAvailable(any(Supplier.class))).thenReturn(properties);
        when(ctx.getBeanProvider(ReactiveHttpClientProperties.class)).thenReturn(propsProvider);

        ObjectProvider<MethodMetadataCache> cacheProvider = mock(ObjectProvider.class);
        when(cacheProvider.getIfAvailable(any(Supplier.class))).thenReturn(new MethodMetadataCache());
        when(ctx.getBeanProvider(MethodMetadataCache.class)).thenReturn(cacheProvider);

        ObjectProvider<DefaultErrorDecoder> errorProvider = mock(ObjectProvider.class);
        when(errorProvider.getIfAvailable(any(Supplier.class))).thenReturn(new DefaultErrorDecoder());
        when(ctx.getBeanProvider(DefaultErrorDecoder.class)).thenReturn(errorProvider);

        ObjectProvider<WebClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable(any(Supplier.class))).thenReturn(WebClient.builder());
        when(ctx.getBeanProvider(WebClient.Builder.class)).thenReturn(builderProvider);

        ObjectProvider<ReactiveHttpClientCustomizer> customizerProvider = mock(ObjectProvider.class);
        when(customizerProvider.orderedStream()).thenReturn(Stream.empty());
        when(ctx.getBeanProvider(ReactiveHttpClientCustomizer.class)).thenReturn(customizerProvider);

        when(ctx.getBean("missingAuthProvider", AuthProvider.class))
                .thenThrow(new NoSuchBeanDefinitionException(AuthProvider.class, "missingAuthProvider"));
        AuthProvider namedAuthProvider = request -> Mono.just(AuthContext.empty());
        when(ctx.getBean("namedAuthProvider", AuthProvider.class)).thenReturn(namedAuthProvider);

        ObjectProvider<ObjectMapper> objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable()).thenReturn(null);
        when(ctx.getBeanProvider(ObjectMapper.class)).thenReturn(objectMapperProvider);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(DiagnosticClient.class);
        factoryBean.setApplicationContext(ctx);
        return factoryBean;
    }

    private ReactiveHttpClientProperties.ClientConfig clientConfig(String baseUrl) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl(baseUrl);
        return config;
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DiagnosticClient {
        @GET("/ping")
        Mono<String> ping();
    }
}
