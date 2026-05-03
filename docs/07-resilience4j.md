# Resilience4j Integration

The starter provides opt-in Resilience4j support per client: retry, circuit breaker, and bulkhead. Individual methods can override the client-level instance names.

---

## Dependencies

Add these to your application's `pom.xml`:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-reactor</artifactId>
</dependency>
```

---

## Enabling resilience for a client

```yaml
reactive:
  http:
    clients:
      user-service:
        resilience:
          enabled: true
          circuit-breaker: user-service   # Resilience4j instance name
          retry: user-service
          bulkhead: user-service
          retry-methods: [GET, HEAD]      # only these verbs are retried
          timeout-ms: 5000                # per-request timeout (0 = disabled)
```

When `enabled: false` (the default), no Resilience4j operators are applied regardless of other settings.

---

## Retry

### Configurable retry methods

Only idempotent-safe methods are retried by default: `GET` and `HEAD`. Override via `retry-methods`:

```yaml
resilience:
  retry-methods: [GET, HEAD, PUT]   # PUT added for idempotent writes
```

Values are normalized (trimmed and uppercased). Set to an empty list to disable method-based filtering (all verbs retried).

### Resilience4j instance configuration

```yaml
resilience4j:
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms
        retry-exceptions:
          - java.net.ConnectException
          - io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException
```

---

## Circuit breaker

```yaml
resilience4j:
  circuit-breaker:
    instances:
      user-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

---

## Bulkhead

```yaml
resilience4j:
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 25
        max-wait-duration: 0
```

---

## Per-method overrides

One client often fronts several endpoints with different resilience requirements. The `@Retry`, `@CircuitBreaker`, and `@Bulkhead` annotations let a single method opt into a different Resilience4j instance:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApi {

    @GET("/users/{id}")
    @Retry("user-read-retry")           // 5 attempts, 100 ms backoff
    @CircuitBreaker("user-read-cb")     // wide open, 50% failure threshold
    Mono<User> getUser(@PathVar("id") long id);

    @POST("/users")
    @Bulkhead("user-write-bulkhead")    // limit concurrent writes
    Mono<User> createUser(@Body NewUser body);
}
```

Corresponding Resilience4j configuration:

```yaml
resilience4j:
  retry:
    instances:
      user-read-retry:
        max-attempts: 5
        wait-duration: 100ms
  circuit-breaker:
    instances:
      user-read-cb:
        failure-rate-threshold: 50
  bulkhead:
    instances:
      user-write-bulkhead:
        max-concurrent-calls: 10
```

### Important: instance validation at startup

All instance names referenced by `@Retry`, `@CircuitBreaker`, and `@Bulkhead` are validated when the proxy is constructed. If any instance is missing, the starter fails fast with an `IllegalStateException` that lists every missing instance name. This prevents typos from silently falling back to a default-configured instance.

Per-method annotations are still gated on the client having `resilience.enabled: true`. Methods without an override inherit the client-level config.

---

## Resilience4j metrics

When both `micrometer-core` and `resilience4j-micrometer` are on the classpath, and the application registers any of `CircuitBreakerRegistry`, `RetryRegistry`, or `BulkheadRegistry` as beans, the starter auto-binds Resilience4j metrics to the shared `MeterRegistry`:

| Metric prefix | Data exposed |
|---|---|
| `resilience4j.circuitbreaker.*` | State (open/half_open/closed), calls, failure rate |
| `resilience4j.retry.*` | Successful / failed attempts, with / without retry |
| `resilience4j.bulkhead.*` | Available concurrent calls, max concurrent calls |

To disable the binding for a specific registry, declare your own `MeterBinder` bean named `reactiveHttpCircuitBreakerMeterBinder` (or the retry / bulkhead equivalent).
