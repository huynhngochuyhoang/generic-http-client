package io.github.huynhngochuyhoang.httpstarter.core;

import java.lang.reflect.Method;

final class ApiRefValidationSupport {

    private ApiRefValidationSupport() {
    }

    static String configPrefix(String clientName, String apiRefName) {
        return "reactive.http.clients.%s.apis[%s]".formatted(clientName, apiRefName);
    }

    static String apiRefContext(Method method, String apiRefName) {
        return "Method " + method + " references @ApiRef(\"" + apiRefName + "\")";
    }
}
