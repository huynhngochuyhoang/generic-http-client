package com.acme.httpstarter.core;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Expands a URI template by substituting path variables and appending query parameters.
 * <p>
 * Used as a utility when a fully-resolved {@link URI} is needed outside of
 * {@link org.springframework.web.reactive.function.client.WebClient}.
 */
public class UriTemplateExpander {

    /**
     * Builds a {@link URI} from the given components.
     *
     * @param baseUrl      service base URL (e.g. {@code https://api.example.com})
     * @param pathTemplate path template that may contain {@code {variable}} placeholders
     * @param pathVars     values for the path placeholders
     * @param queryParams  additional query parameters (nulls already filtered out)
     * @return fully expanded and encoded URI
     */
    public URI expand(String baseUrl, String pathTemplate,
                      Map<String, Object> pathVars, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + pathTemplate);
        queryParams.forEach((k, v) -> builder.queryParam(k, v));
        return builder.buildAndExpand(pathVars).encode().toUri();
    }
}
