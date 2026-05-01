package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache that parses and stores {@link MethodMetadata} for each interface method.
 */
public class MethodMetadataCache {

    private final ConcurrentHashMap<Method, MethodMetadata> cache = new ConcurrentHashMap<>();

    public MethodMetadata get(Method method) {
        return cache.computeIfAbsent(method, this::parse);
    }

    private MethodMetadata parse(Method method) {
        MethodMetadata meta = new MethodMetadata();
        meta.setMethod(method);
        meta.setApiName(method.getName());

        // ---- HTTP verb ----
        if (method.isAnnotationPresent(GET.class)) {
            meta.setHttpMethod("GET");
            meta.setPathTemplate(method.getAnnotation(GET.class).value());
        } else if (method.isAnnotationPresent(POST.class)) {
            meta.setHttpMethod("POST");
            meta.setPathTemplate(method.getAnnotation(POST.class).value());
        } else if (method.isAnnotationPresent(PUT.class)) {
            meta.setHttpMethod("PUT");
            meta.setPathTemplate(method.getAnnotation(PUT.class).value());
        } else if (method.isAnnotationPresent(DELETE.class)) {
            meta.setHttpMethod("DELETE");
            meta.setPathTemplate(method.getAnnotation(DELETE.class).value());
        } else if (method.isAnnotationPresent(PATCH.class)) {
            meta.setHttpMethod("PATCH");
            meta.setPathTemplate(method.getAnnotation(PATCH.class).value());
        }

        // ---- Parameters ----
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof PathVar pv) {
                    meta.getPathVars().put(i, pv.value());
                } else if (ann instanceof QueryParam qp) {
                    meta.getQueryParams().put(i, qp.value());
                } else if (ann instanceof HeaderParam hp) {
                    if (Map.class.isAssignableFrom(parameterTypes[i])) {
                        if (hp.value() != null && !hp.value().isBlank()) {
                            throw new IllegalArgumentException(
                                    "@HeaderParam value must be blank for Map parameter at index " + i + " in method: " + method);
                        }
                        meta.getHeaderMapParams().add(i);
                    } else {
                        if (hp.value() == null || hp.value().isBlank()) {
                            throw new IllegalArgumentException(
                                    "@HeaderParam value must not be blank for non-Map parameter at index " + i + " in method: " + method);
                        }
                        meta.getHeaderParams().put(i, hp.value());
                    }
                } else if (ann instanceof Body) {
                    meta.setBodyIndex(i);
                } else if (ann instanceof FormField ff) {
                    if (ff.value() == null || ff.value().isBlank()) {
                        throw new IllegalArgumentException(
                                "@FormField value must not be blank for parameter at index " + i + " in method: " + method);
                    }
                    meta.getFormFieldParams().put(i, ff.value());
                } else if (ann instanceof FormFile ff) {
                    if (ff.value() == null || ff.value().isBlank()) {
                        throw new IllegalArgumentException(
                                "@FormFile value must not be blank for parameter at index " + i + " in method: " + method);
                    }
                    meta.getFormFileParams().put(i, ff);
                }
            }
        }

        if (method.isAnnotationPresent(MultipartBody.class)) {
            meta.setMultipart(true);
            if (meta.getBodyIndex() >= 0) {
                throw new IllegalStateException(
                        "@MultipartBody cannot be combined with a @Body parameter on method: " + method);
            }
            if (meta.getFormFieldParams().isEmpty() && meta.getFormFileParams().isEmpty()) {
                throw new IllegalStateException(
                        "@MultipartBody method has no @FormField / @FormFile parameters: " + method);
            }
        } else if (!meta.getFormFieldParams().isEmpty() || !meta.getFormFileParams().isEmpty()) {
            throw new IllegalStateException(
                    "@FormField / @FormFile parameters require the method to be annotated @MultipartBody: " + method);
        }

        // ---- Return type ----
        Class<?> declaredReturnType = method.getReturnType();
        meta.setReturnsMono(Mono.class.isAssignableFrom(declaredReturnType));
        meta.setReturnsFlux(Flux.class.isAssignableFrom(declaredReturnType));
        if (!meta.isReturnsMono() && !meta.isReturnsFlux()) {
            throw new IllegalStateException("Method " + method + " must return Mono<T> or Flux<T>");
        }

        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            meta.setReturnsMono(Mono.class.isAssignableFrom(rawType));
            meta.setReturnsFlux(Flux.class.isAssignableFrom(rawType));
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                meta.setResponseType(args[0]);
            }
        }

        if (method.isAnnotationPresent(LogHttpExchange.class)) {
            LogHttpExchange ann = method.getAnnotation(LogHttpExchange.class);
            meta.setHttpExchangeLoggingEnabled(true);
            meta.setHttpExchangeLoggerClass(ann.logger());
        }

        ApiName apiName = method.getAnnotation(ApiName.class);
        if (apiName != null) {
            meta.setApiName(apiName.value());
        }

        TimeoutMs timeoutMs = method.getAnnotation(TimeoutMs.class);
        if (timeoutMs != null) {
            if (timeoutMs.value() < 0) {
                throw new IllegalArgumentException(
                        "@TimeoutMs value must be non-negative (>= 0) for method: " + method);
            }
            meta.setTimeoutMs(timeoutMs.value());
        }

        Retry retry = method.getAnnotation(Retry.class);
        if (retry != null) {
            requireNonBlankAnnotationValue(retry.value(), "@Retry", method);
            meta.setRetryInstanceName(retry.value());
        }
        CircuitBreaker circuitBreaker = method.getAnnotation(CircuitBreaker.class);
        if (circuitBreaker != null) {
            requireNonBlankAnnotationValue(circuitBreaker.value(), "@CircuitBreaker", method);
            meta.setCircuitBreakerInstanceName(circuitBreaker.value());
        }
        Bulkhead bulkhead = method.getAnnotation(Bulkhead.class);
        if (bulkhead != null) {
            requireNonBlankAnnotationValue(bulkhead.value(), "@Bulkhead", method);
            meta.setBulkheadInstanceName(bulkhead.value());
        }

        meta.freezeCollections();
        return meta;
    }

    private static void requireNonBlankAnnotationValue(String value, String annotationName, Method method) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    annotationName + " value must not be blank on method: " + method);
        }
    }
}
