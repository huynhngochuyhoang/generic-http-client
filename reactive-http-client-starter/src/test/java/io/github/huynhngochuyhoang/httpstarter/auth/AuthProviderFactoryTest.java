package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProviderFactoryTest {

    @Test
    void awsSigV4FactoryCreatesProvider() {
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("aws-sigv4");
        auth.getAwsSigV4().setAccessKeyId("key");
        auth.getAwsSigV4().setSecretAccessKey("secret");
        auth.getAwsSigV4().setRegion("us-east-1");
        auth.getAwsSigV4().setService("execute-api");

        AuthProvider provider = new AwsSigV4AuthProviderFactory()
                .create("payments", auth, WebClient.builder());

        assertThat(provider).isInstanceOf(AwsSigV4AuthProvider.class);
    }

    @Test
    void oauth2FactoryCreatesRefreshingBearerProvider() {
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        auth.getOauth2ClientCredentials().setTokenUri("https://auth.example.com/oauth/token");
        auth.getOauth2ClientCredentials().setClientId("client");
        auth.getOauth2ClientCredentials().setClientSecret("secret");
        auth.getOauth2ClientCredentials().setAuthStyle("form-post");

        AuthProvider provider = new OAuth2ClientCredentialsAuthProviderFactory()
                .create("users", auth, WebClient.builder());

        assertThat(provider).isInstanceOf(RefreshingBearerAuthProvider.class);
    }
}
