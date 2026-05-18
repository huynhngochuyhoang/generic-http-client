package io.github.huynhngochuyhoang.httpstarter.core;

/**
 * Ordered, failure-isolated lifecycle callbacks around client invocations.
 */
public interface ReactiveHttpClientLifecycleHook {

    /**
     * Return {@code false} to skip this hook for a client. Defaults to all clients.
     */
    default boolean supports(String clientName) {
        return true;
    }

    default void onStart(ReactiveHttpClientLifecycleContext context) {
    }

    default void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
    }

    default void onSuccess(ReactiveHttpClientLifecycleContext context) {
    }

    default void onError(ReactiveHttpClientLifecycleContext context) {
    }

    default void onCancel(ReactiveHttpClientLifecycleContext context) {
    }
}
