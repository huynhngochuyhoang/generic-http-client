# Per-Client WebClient Customizer

`ReactiveHttpClientCustomizer` is an extension point that lets you apply arbitrary
`WebClient.Builder` customizations — including custom `ExchangeFilterFunction`s — to
one or more reactive HTTP clients **without recreating a raw `WebClient`** and losing
all starter-managed filters and configuration.

---

## Overview

Register any number of `ReactiveHttpClientCustomizer` beans in your Spring context.
During proxy construction, `ReactiveHttpClientFactoryBean` will:

1. Collect every `ReactiveHttpClientCustomizer` bean in `@Order / Ordered` sequence.
2. Call `supports(clientName)` on each one.
3. Apply `customize(builder)` on every customizer that returned `true`.

Customizers run **after** all built-in filters (correlation-ID propagation, outbound
auth, exchange logging) are wired, so custom filters added here sit at the outermost
position in the filter chain.

---

## Interface

```java
@FunctionalInterface
public interface ReactiveHttpClientCustomizer {

    /**
     * Return {@code false} to skip this client. Defaults to {@code true} (apply to all).
     */
    default boolean supports(String clientName) {
        return true;
    }

    void customize(WebClient.Builder builder);
}
```

---

## Adding a custom filter to one specific client

```java
@Component
public class RequestSigningCustomizer implements ReactiveHttpClientCustomizer {

    private final HmacSigner signer;

    public RequestSigningCustomizer(HmacSigner signer) {
        this.signer = signer;
    }

    @Override
    public boolean supports(String clientName) {
        return "payment-service".equals(clientName);
    }

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter((request, next) -> {
            ClientRequest signed = ClientRequest.from(request)
                .header("X-Signature", signer.sign(request))
                .build();
            return next.exchange(signed);
        });
    }
}
```

No extra configuration is required — registering the bean is sufficient.

---

## Applying a customizer to all clients

Omit the `supports()` override. The default implementation returns `true` for every
client name:

```java
@Component
public class DebugHeaderCustomizer implements ReactiveHttpClientCustomizer {

    @Override
    public void customize(WebClient.Builder builder) {
        builder.defaultHeader("X-Debug-Source", "reactive-http-client");
    }
}
```

Lambdas are equivalent because `ReactiveHttpClientCustomizer` is a `@FunctionalInterface`:

```java
@Bean
ReactiveHttpClientCustomizer addDebugHeader() {
    return builder -> builder.defaultHeader("X-Debug-Source", "reactive-http-client");
}
```

---

## Controlling execution order

Use Spring's standard `@Order` annotation or implement `org.springframework.core.Ordered`.
Lower values run first.

```java
@Component
@Order(1)
public class TracingFilterCustomizer implements ReactiveHttpClientCustomizer {
    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(tracingFilter());
    }
}

@Component
@Order(2)
public class AuditFilterCustomizer implements ReactiveHttpClientCustomizer {
    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(auditFilter());
    }
}
```

When no `@Order` is declared, Spring's default bean-registration order applies.

---

## Targeting multiple clients

```java
@Component
public class InternalServiceCustomizer implements ReactiveHttpClientCustomizer {

    private static final Set<String> INTERNAL = Set.of("order-service", "inventory-service");

    @Override
    public boolean supports(String clientName) {
        return INTERNAL.contains(clientName);
    }

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(internalServiceFilter());
    }
}
```
