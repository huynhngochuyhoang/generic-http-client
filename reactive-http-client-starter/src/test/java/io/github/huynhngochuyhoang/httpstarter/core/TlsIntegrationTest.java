package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import javax.net.ssl.SSLHandshakeException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;

class TlsIntegrationTest {

    @Test
    void trustedSelfSignedPeerSucceeds() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        DisposableServer server = startHttpsServer(certificate);
        Path trustStore = writeTrustStore(certificate.cert(), "changeit");
        try {
            ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
            tls.setTrustStore("file:" + trustStore.toAbsolutePath());
            tls.setTrustStorePassword("changeit");
            tls.setTrustStoreType("PKCS12");

            HttpClient httpClient = TlsContextApplier.apply(HttpClient.create(), tls, "tls-test-client");
            WebClient client = WebClient.builder()
                    .baseUrl("https://localhost:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            StepVerifier.create(client.get().uri("/ping").retrieve().bodyToMono(String.class))
                    .expectNext("pong")
                    .verifyComplete();
        } finally {
            Files.deleteIfExists(trustStore);
            server.disposeNow();
            certificate.delete();
        }
    }

    @Test
    void trustedSelfSignedPeerSucceedsWithHttp2AndCustomTls() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        DisposableServer server = startHttp2HttpsServer(certificate);
        Path trustStore = writeTrustStore(certificate.cert(), "changeit");
        try {
            ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
            tls.setTrustStore("file:" + trustStore.toAbsolutePath());
            tls.setTrustStorePassword("changeit");
            tls.setTrustStoreType("PKCS12");

            HttpClient httpClient = TlsContextApplier.apply(
                    HttpClient.create().protocol(HttpProtocol.H2), tls, "tls-http2-test-client");
            WebClient client = WebClient.builder()
                    .baseUrl("https://localhost:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            StepVerifier.create(client.get().uri("/ping").retrieve().bodyToMono(String.class))
                    .expectNext("pong")
                    .verifyComplete();
        } finally {
            Files.deleteIfExists(trustStore);
            server.disposeNow();
            certificate.delete();
        }
    }

    @Test
    void untrustedSelfSignedPeerFailsHandshake() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        DisposableServer server = startHttpsServer(certificate);
        try {
            WebClient client = WebClient.builder()
                    .baseUrl("https://localhost:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                    .build();

            StepVerifier.create(client.get().uri("/ping").retrieve().bodyToMono(String.class))
                    .expectErrorMatches(this::isHandshakeFailure)
                    .verify();
        } finally {
            server.disposeNow();
            certificate.delete();
        }
    }

    private DisposableServer startHttp2HttpsServer(SelfSignedCertificate certificate) {
        return HttpServer.create()
                .host("localhost")
                .port(0)
                .protocol(HttpProtocol.H2)
                .secure(spec -> spec.sslContext(Http2SslContextSpec.forServer(
                        certificate.certificate(), certificate.privateKey())))
                .route(routes -> routes.get("/ping", (request, response) -> response.sendString(Mono.just("pong"))))
                .bindNow();
    }

    private DisposableServer startHttpsServer(SelfSignedCertificate certificate) throws Exception {
        return HttpServer.create()
                .host("localhost")
                .port(0)
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build());
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to build test server SSL context", e);
                    }
                })
                .route(routes -> routes.get("/ping", (request, response) -> response.sendString(Mono.just("pong"))))
                .bindNow();
    }

    private Path writeTrustStore(Certificate certificate, String password) throws Exception {
        Path file = Files.createTempFile("reactive-http-client-truststore-", ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("server", certificate);
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            store.store(out, password.toCharArray());
        }
        return file;
    }

    private boolean isHandshakeFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SSLHandshakeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
