package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class ClientNameValidator {

    public static final String ALLOWED_PATTERN_DESCRIPTION = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    private static final Pattern ALLOWED = Pattern.compile(ALLOWED_PATTERN_DESCRIPTION);

    private ClientNameValidator() {
    }

    public static void validate(String clientName, String context) {
        if (!StringUtils.hasText(clientName)) {
            throw new IllegalArgumentException(context + " client name must not be blank");
        }
        if (!ALLOWED.matcher(clientName).matches()) {
            throw new IllegalArgumentException(context + " client name '" + clientName
                    + "' is invalid. Allowed pattern: " + ALLOWED_PATTERN_DESCRIPTION);
        }
    }
}
