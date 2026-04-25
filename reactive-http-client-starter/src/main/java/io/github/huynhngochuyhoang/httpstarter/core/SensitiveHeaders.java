package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.Locale;
import java.util.Set;

/**
 * Shared list of header names that typically carry credentials or session material
 * and should be redacted before they land in logs or log contexts.
 *
 * <p>Used by {@link DefaultHttpExchangeLogger} for outbound request/response headers
 * and by {@link io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter}
 * as the default deny-list for inbound headers.
 */
public final class SensitiveHeaders {

    /** Default deny-list, lower-cased for case-insensitive matching. */
    public static final Set<String> DEFAULTS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key"
    );

    private SensitiveHeaders() {}

    /** {@code true} if {@code headerName} matches an entry in {@link #DEFAULTS} (case-insensitive). */
    public static boolean isSensitive(String headerName) {
        return headerName != null && DEFAULTS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    /** {@code true} if {@code headerName} matches any entry in {@code denyList} (case-insensitive). */
    public static boolean isSensitive(String headerName, Set<String> denyList) {
        if (headerName == null || denyList == null || denyList.isEmpty()) {
            return false;
        }
        return denyList.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
