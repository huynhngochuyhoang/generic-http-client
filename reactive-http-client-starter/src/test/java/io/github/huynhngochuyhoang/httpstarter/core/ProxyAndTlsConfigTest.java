package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level coverage for the proxy + TLS wiring introduced in roadmap 1.5.
 *
 * <p>Focuses on the resolution priority rules and the applier classes producing
 * a working {@link HttpClient} without hitting the network. End-to-end behaviour
 * (real proxy / mTLS handshake) is out of scope.
 */
class ProxyAndTlsConfigTest {

    // ---- Resolution priority ----------------------------------------------

    @Test
    void perClientProxyWinsOverGlobal() {
        ReactiveHttpClientProperties.NetworkConfig network = new ReactiveHttpClientProperties.NetworkConfig();
        network.setProxy(proxyAt("global.example", 8080));

        ReactiveHttpClientProperties.ClientConfig client = new ReactiveHttpClientProperties.ClientConfig();
        client.setProxy(proxyAt("override.example", 9090));

        ReactiveHttpClientProperties.ProxyConfig resolved =
                ReactiveHttpClientFactoryBean.resolveProxy(client, network);

        assertThat(resolved.getHost()).isEqualTo("override.example");
        assertThat(resolved.getPort()).isEqualTo(9090);
    }

    @Test
    void noProxyConfiguredResolvesToNull() {
        ReactiveHttpClientProperties.ProxyConfig resolved =
                ReactiveHttpClientFactoryBean.resolveProxy(
                        new ReactiveHttpClientProperties.ClientConfig(),
                        new ReactiveHttpClientProperties.NetworkConfig());
        assertThat(resolved).isNull();
    }

    @Test
    void perClientTlsWinsOverGlobal() {
        ReactiveHttpClientProperties.NetworkConfig network = new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.TlsConfig globalTls = new ReactiveHttpClientProperties.TlsConfig();
        globalTls.setTrustStore("classpath:global-ts.p12");
        network.setTls(globalTls);

        ReactiveHttpClientProperties.ClientConfig client = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.TlsConfig clientTls = new ReactiveHttpClientProperties.TlsConfig();
        clientTls.setTrustStore("classpath:override-ts.p12");
        client.setTls(clientTls);

        ReactiveHttpClientProperties.TlsConfig resolved =
                ReactiveHttpClientFactoryBean.resolveTls(client, network);

        assertThat(resolved.getTrustStore()).isEqualTo("classpath:override-ts.p12");
    }

    // ---- ProxyApplier ------------------------------------------------------

    @Test
    void httpProxyApplierMapsTypeAndReturnsConfiguredClient() {
        ReactiveHttpClientProperties.ProxyConfig proxy = proxyAt("proxy.example", 3128);
        proxy.setUsername("u");
        proxy.setPassword("p");
        proxy.setNonProxyHosts("localhost|.*\\.internal");

        HttpClient configured = HttpProxyApplier.apply(HttpClient.create(), proxy);

        assertThat(configured)
                .as("apply() must return a different HttpClient instance with proxy attached")
                .isNotNull()
                .isNotSameAs(HttpClient.create());
        assertThat(configured.configuration().hasProxy())
                .as("Reactor Netty configuration must report hasProxy() == true")
                .isTrue();
    }

    @Test
    void proxyTypeMappingHandlesAllValues() {
        assertThat(HttpProxyApplier.mapProxyType(ReactiveHttpClientProperties.ProxyConfig.Type.HTTP))
                .isEqualTo(ProxyProvider.Proxy.HTTP);
        assertThat(HttpProxyApplier.mapProxyType(ReactiveHttpClientProperties.ProxyConfig.Type.HTTPS))
                .isEqualTo(ProxyProvider.Proxy.HTTP);
        assertThat(HttpProxyApplier.mapProxyType(ReactiveHttpClientProperties.ProxyConfig.Type.SOCKS4))
                .isEqualTo(ProxyProvider.Proxy.SOCKS4);
        assertThat(HttpProxyApplier.mapProxyType(ReactiveHttpClientProperties.ProxyConfig.Type.SOCKS5))
                .isEqualTo(ProxyProvider.Proxy.SOCKS5);
        assertThat(HttpProxyApplier.mapProxyType(null))
                .as("null type defaults to HTTP")
                .isEqualTo(ProxyProvider.Proxy.HTTP);
    }

    // ---- TlsContextApplier -------------------------------------------------

    @Test
    void tlsApplierWithInsecureTrustAllReturnsConfiguredClient() {
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setInsecureTrustAll(true);

        HttpClient configured = TlsContextApplier.apply(HttpClient.create(), tls, "test-client");

        assertThat(configured).isNotNull();
        assertThat(configured.configuration().sslProvider())
                .as("apply() must register an SSL provider on the HttpClient")
                .isNotNull();
    }

    @Test
    void tlsApplierWithProtocolsAndCiphersBuilds() {
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setInsecureTrustAll(true);
        tls.setProtocols(List.of("TLSv1.3", "TLSv1.2"));
        tls.setCiphers(List.of()); // empty list → JDK defaults

        HttpClient configured = TlsContextApplier.apply(HttpClient.create(), tls, "test-client");
        assertThat(configured.configuration().sslProvider()).isNotNull();
    }

    @Test
    void tlsApplierFailsFastWhenTrustStoreMissing() {
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setTrustStore("classpath:does-not-exist.p12");
        tls.setTrustStorePassword("changeit");

        assertThatThrownBy(() -> TlsContextApplier.apply(HttpClient.create(), tls, "test-client"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to build SSL context")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void tlsApplierLoadsTrustStoreFromGeneratedFileResource() throws Exception {
        Path trustStorePath = generateEmptyKeyStore("temp-truststore.p12", "changeit");
        try {
            ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
            tls.setTrustStore("file:" + trustStorePath.toAbsolutePath());
            tls.setTrustStorePassword("changeit");
            tls.setTrustStoreType("PKCS12");

            HttpClient configured = TlsContextApplier.apply(HttpClient.create(), tls, "test-client");
            assertThat(configured.configuration().sslProvider()).isNotNull();
        } finally {
            Files.deleteIfExists(trustStorePath);
        }
    }

    // ---- Property binding --------------------------------------------------

    @Test
    void proxyAndTlsConfigBindFromYaml() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.network.proxy.type", "SOCKS5");
        yaml.put("reactive.http.network.proxy.host", "socks.example");
        yaml.put("reactive.http.network.proxy.port", 1080);
        yaml.put("reactive.http.network.tls.insecure-trust-all", true);
        yaml.put("reactive.http.network.tls.protocols", List.of("TLSv1.3"));
        yaml.put("reactive.http.clients.special.proxy.type", "NONE");
        yaml.put("reactive.http.clients.special.tls.trust-store", "classpath:per-client-ts.p12");

        ReactiveHttpClientProperties bound = bind(yaml);

        ReactiveHttpClientProperties.ProxyConfig globalProxy = bound.getNetwork().getProxy();
        assertThat(globalProxy).isNotNull();
        assertThat(globalProxy.getType()).isEqualTo(ReactiveHttpClientProperties.ProxyConfig.Type.SOCKS5);
        assertThat(globalProxy.getHost()).isEqualTo("socks.example");
        assertThat(globalProxy.getPort()).isEqualTo(1080);

        assertThat(bound.getNetwork().getTls().isInsecureTrustAll()).isTrue();
        assertThat(bound.getNetwork().getTls().getProtocols()).containsExactly("TLSv1.3");

        ReactiveHttpClientProperties.ClientConfig special = bound.getClients().get("special");
        assertThat(special.getProxy().getType()).isEqualTo(ReactiveHttpClientProperties.ProxyConfig.Type.NONE);
        assertThat(special.getTls().getTrustStore()).isEqualTo("classpath:per-client-ts.p12");
    }

    // ---- Helpers -----------------------------------------------------------

    private static ReactiveHttpClientProperties.ProxyConfig proxyAt(String host, int port) {
        ReactiveHttpClientProperties.ProxyConfig p = new ReactiveHttpClientProperties.ProxyConfig();
        p.setHost(host);
        p.setPort(port);
        return p;
    }

    private static Path generateEmptyKeyStore(String fileName, String password) throws Exception {
        Path file = Files.createTempFile(fileName.replace(".p12", "-"), ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            store.store(out, password.toCharArray());
        }
        return file;
    }

    private static ReactiveHttpClientProperties bind(Map<String, Object> yaml) {
        org.springframework.boot.context.properties.source.ConfigurationPropertySource src =
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(yaml);
        return new org.springframework.boot.context.properties.bind.Binder(src)
                .bindOrCreate("reactive.http",
                        org.springframework.boot.context.properties.bind.Bindable.of(ReactiveHttpClientProperties.class));
    }
}
