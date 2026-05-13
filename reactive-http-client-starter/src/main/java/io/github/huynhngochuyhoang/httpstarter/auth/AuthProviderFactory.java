package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory SPI for creating {@link AuthProvider} instances from object-style
 * client auth configuration.
 */
public interface AuthProviderFactory {

    /**
     * Returns {@code true} when this factory can create providers for the
     * configured auth type.
     */
    boolean supports(String type);

    /**
     * Creates an auth provider for a configured client.
     *
     * @param clientName logical reactive HTTP client name
     * @param config object-style auth configuration
     * @param webClientBuilder builder for provider-owned HTTP calls, such as OAuth2 token requests
     * @return provider to attach to the client
     */
    AuthProvider create(String clientName,
                        ReactiveHttpClientProperties.AuthConfig config,
                        WebClient.Builder webClientBuilder);
}
