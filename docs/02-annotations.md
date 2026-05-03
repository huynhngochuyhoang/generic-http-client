# Annotation Reference

All annotations live in `io.github.huynhngochuyhoang.httpstarter.annotation`.

---

## Client declaration

### `@ReactiveHttpClient`

Marks an interface as a reactive HTTP client managed by the starter.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | `""` | Logical client name; must match a key in `reactive.http.clients` |
| `baseUrl` | `String` | `""` | Hard-coded base URL (overrides the config entry) |

Either `name` (resolved from config) or `baseUrl` (literal) must be supplied.

```java
// Name-based: base-url comes from reactive.http.clients.payment-service
@ReactiveHttpClient(name = "payment-service")
public interface PaymentClient { ... }

// URL-based: useful for tests or when you don't want a config entry
@ReactiveHttpClient(baseUrl = "http://localhost:8080")
public interface LocalApiClient { ... }
```

---

## HTTP verb annotations

Each annotation accepts a single `value` attribute: the path template.

| Annotation | HTTP method |
|---|---|
| `@GET(path)` | GET |
| `@POST(path)` | POST |
| `@PUT(path)` | PUT |
| `@DELETE(path)` | DELETE |
| `@PATCH(path)` | PATCH |

Path templates support `{variable}` placeholders resolved from `@PathVar` parameters.

```java
@GET("/orders/{orderId}/items/{itemId}")
Mono<OrderItem> getItem(
        @PathVar("orderId") long orderId,
        @PathVar("itemId") long itemId);
```

---

## Parameter annotations

### `@PathVar`

Binds a method parameter to a `{name}` placeholder in the path template.

```java
@GET("/users/{id}")
Mono<User> getUser(@PathVar("id") String id);
```

### `@QueryParam`

Appends a query parameter to the request URL. `null` values are omitted.

```java
@GET("/users")
Mono<List<User>> listUsers(
        @QueryParam("page") int page,
        @QueryParam("size") int size,
        @QueryParam("role") String role);   // omitted when null
```

### `@HeaderParam`

Adds a static or dynamic request header. Accepts a plain value or a `Map<String, String>` for multiple headers at once.

```java
// single header
@GET("/reports")
Mono<Report> getReport(@HeaderParam("X-Tenant") String tenant);

// dynamic header map – each entry becomes its own request header
@POST("/events")
Mono<Void> publish(
        @Body Event event,
        @HeaderParam Map<String, String> extraHeaders);
```

### `@Body`

Marks the parameter that provides the request body. Only one `@Body` parameter is allowed per method; combining `@Body` with `@MultipartBody` on the same method is rejected at startup.

```java
@POST("/users")
Mono<User> createUser(@Body CreateUserRequest request);
```

### `@FormField`

Scalar or multi-value text part of a `@MultipartBody` request.

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | Part name |

```java
@FormField("description") String description
```

### `@FormFile`

File part of a `@MultipartBody` request.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `value` | `String` | — | Part name |
| `filename` | `String` | `"file"` | Fallback filename sent in `Content-Disposition` |
| `contentType` | `String` | `"application/octet-stream"` | Fallback `Content-Type` |

Accepted parameter types: `byte[]`, any `org.springframework.core.io.Resource`, or `FileAttachment` (carries bytes + filename + content-type, overriding the annotation defaults).

See [10-multipart.md](10-multipart.md) for full examples.

---

## Method-level annotations

### `@ApiName`

Sets a human-readable logical name used as the `api.name` tag in metrics and as the span name in traces.

```java
@GET("/users/{id}")
@ApiName("user.getById")
Mono<User> getUser(@PathVar("id") long id);
```

Defaults to the Java method name when omitted.

### `@TimeoutMs`

Per-method response timeout in milliseconds. Overrides `resilience.timeout-ms`. `0` disables the per-request timeout for that method without touching the global safety-net timeouts.

```java
@GET("/users/{id}")
@TimeoutMs(3000)          // fail fast in 3 s
Mono<User> getUser(@PathVar("id") long id);

@POST("/batch-import")
@TimeoutMs(0)             // no per-request timeout; safety-net still applies
Mono<ImportReceipt> batchImport(@Body ImportRequest request);
```

See [04-timeouts.md](04-timeouts.md) for the full timeout-layer precedence model.

### `@MultipartBody`

Marks the method as a `multipart/form-data` request. Combine with `@FormField` and `@FormFile` parameters. See [10-multipart.md](10-multipart.md).

### `@LogHttpExchange`

Hooks per-method request/response logging via an `HttpExchangeLogger` bean. See [13-exchange-logging.md](13-exchange-logging.md).

---

## Resilience overrides (method-level)

These annotations let a single method use a different Resilience4j instance than the client-level default. The client must still have `resilience.enabled: true`.

### `@Retry`

```java
@GET("/users/{id}")
@Retry("user-read-retry")     // must be configured under resilience4j.retry.instances
Mono<User> getUser(@PathVar("id") long id);
```

### `@CircuitBreaker`

```java
@GET("/users/{id}")
@CircuitBreaker("user-read-cb")
Mono<User> getUser(@PathVar("id") long id);
```

### `@Bulkhead`

```java
@POST("/users")
@Bulkhead("user-write-bulkhead")
Mono<User> createUser(@Body NewUser body);
```

Per-method annotations take precedence over `resilience.retry` / `.circuit-breaker` / `.bulkhead`. The starter validates all referenced instances at proxy-construction time and fails fast with a descriptive `IllegalStateException` for every missing instance, so typos cannot silently fall back to a default-configured instance.

See [07-resilience4j.md](07-resilience4j.md) for full usage and configuration.
