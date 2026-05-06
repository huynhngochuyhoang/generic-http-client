# Error Handling

The starter maps every non-2xx response and network-level failure to a typed exception so callers can handle errors uniformly without inspecting raw `WebClientResponseException`.

---

## Exception hierarchy

```
RuntimeException
 └─ HttpClientException      – 4xx responses
 └─ RemoteServiceException   – 5xx responses
```

Both types expose:

| Method | Returns | Description |
|---|---|---|
| `getStatusCode()` | `int` | HTTP status code |
| `getResponseBody()` | `String` | Raw response body (may be empty) |
| `getErrorCategory()` | `ErrorCategory` | Coarse-grained failure category |

---

## Error categories

`ErrorCategory` is an enum with the following values:

| Category | When |
|---|---|
| `RATE_LIMITED` | HTTP 429 response |
| `CLIENT_ERROR` | Other 4xx response |
| `SERVER_ERROR` | 5xx response |
| `TIMEOUT` | `TimeoutException` or `ReadTimeoutException` |
| `CONNECT_ERROR` | `ConnectException` — TCP connection refused / timed out |
| `UNKNOWN_HOST` | `UnknownHostException` — DNS resolution failed |
| `AUTH_PROVIDER_ERROR` | `AuthProviderException` — token fetch / signing failed |
| `RESPONSE_DECODE_ERROR` | Codec/deserialization error on a 2xx response |
| `CANCELLED` | Reactive subscription cancelled before completion |
| `UNKNOWN` | Any other uncategorized error |

---

## Reacting to errors in calling code

```java
userApiClient.getUser(id)
    .onErrorResume(HttpClientException.class, ex -> {
        if (ex.getErrorCategory() == ErrorCategory.RATE_LIMITED) {
            // back off and retry from the caller
        }
        return Mono.error(ex);
    })
    .onErrorResume(RemoteServiceException.class, ex -> {
        log.error("user-service returned {}: {}", ex.getStatusCode(), ex.getResponseBody());
        return Mono.error(ex);
    });
```

---

## Handling auth errors

`AuthProviderException` is thrown when the `AuthProvider` fails (token endpoint unreachable, credentials invalid, or the token returned is already expired). It wraps the original cause and carries the logical client name:

```java
userApiClient.getUser(id)
    .onErrorResume(AuthProviderException.class, ex -> {
        log.warn("Auth failed for {}: {}", ex.getClientName(), ex.getCause().getMessage());
        return Mono.error(ex);
    });
```

---

## Handling request serialization errors

`RequestSerializationException` is thrown when the request body cannot be serialized before the HTTP call is made. This is a programming error (wrong content type, missing codec, or a type the codec cannot handle) rather than a runtime error, so it surfaces early:

```java
userApiClient.createUser(badPayload)
    .onErrorResume(RequestSerializationException.class, ex -> { ... });
```

---

## Observability and error categories

The `error.category` tag on the `reactive.http.client.requests` timer and the `error.type` attribute on OTel spans both reflect `ErrorCategory`. This makes error-rate dashboards and alerts easy to slice by failure type (e.g. alert on `SERVER_ERROR` rate > 5 %, ignore `RATE_LIMITED` from alert but feed it into a backpressure dashboard).

See [08-observability.md](08-observability.md) for the full metrics reference.
