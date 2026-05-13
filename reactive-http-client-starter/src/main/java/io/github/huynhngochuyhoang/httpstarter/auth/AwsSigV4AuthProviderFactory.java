package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Built-in factory for {@code type: aws-sigv4}.
 */
public final class AwsSigV4AuthProviderFactory implements AuthProviderFactory {

    public static final String TYPE = "aws-sigv4";

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public AuthProvider create(String clientName,
                               ReactiveHttpClientProperties.AuthConfig config,
                               WebClient.Builder webClientBuilder) {
        ReactiveHttpClientProperties.AwsSigV4AuthConfig aws = config.getAwsSigV4();
        return AwsSigV4AuthProvider.builder()
                .accessKeyId(aws.getAccessKeyId())
                .secretAccessKey(aws.getSecretAccessKey())
                .sessionToken(aws.getSessionToken())
                .region(aws.getRegion())
                .service(aws.getService())
                .build();
    }
}
