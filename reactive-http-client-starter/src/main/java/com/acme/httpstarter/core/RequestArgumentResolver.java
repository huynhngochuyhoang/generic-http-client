package com.acme.httpstarter.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves method invocation arguments into structured maps according to the annotations
 * captured in {@link MethodMetadata}.
 */
public class RequestArgumentResolver {

    public ResolvedArgs resolve(MethodMetadata meta, Object[] args) {
        Map<String, Object> pathVars = new LinkedHashMap<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        Object body = null;

        for (Map.Entry<Integer, String> entry : meta.getPathVars().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                pathVars.put(entry.getValue(), args[idx]);
            }
        }

        for (Map.Entry<Integer, String> entry : meta.getQueryParams().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                queryParams.put(entry.getValue(), args[idx]);
            }
        }

        for (Map.Entry<Integer, String> entry : meta.getHeaderParams().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                headers.put(entry.getValue(), String.valueOf(args[idx]));
            }
        }

        if (meta.getBodyIndex() >= 0 && args != null && meta.getBodyIndex() < args.length) {
            body = args[meta.getBodyIndex()];
        }

        return new ResolvedArgs(pathVars, queryParams, headers, body);
    }

    /**
     * Container for the arguments extracted from a single method invocation.
     */
    public record ResolvedArgs(
            Map<String, Object> pathVars,
            Map<String, Object> queryParams,
            Map<String, String> headers,
            Object body
    ) {}
}
