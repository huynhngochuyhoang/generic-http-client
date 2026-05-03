# Streaming Responses

Methods that declare `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>` as their return type bypass the in-memory codec entirely. Payloads of any size are streamed without risk of a `DataBufferLimitException`, regardless of the `codec-max-in-memory-size-mb` setting.

---

## Streaming to a `Flux<DataBuffer>`

```java
@ReactiveHttpClient(name = "object-store")
public interface ObjectStoreClient {

    @GET("/objects/{key}")
    Flux<DataBuffer> download(@PathVar("key") String key);
}
```

The caller receives `DataBuffer` chunks as Reactor Netty produces them. Buffers are released automatically as the consumer drives the `Flux`.

---

## Streaming with response status and headers

Use `Mono<ResponseEntity<Flux<DataBuffer>>>` to expose the upstream HTTP status and response headers alongside the streaming body. This is useful for proxy / pass-through implementations:

```java
@ReactiveHttpClient(name = "object-store")
public interface ObjectStoreClient {

    @GET("/objects/{key}")
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadEntity(@PathVar("key") String key);
}
```

Usage in a Spring WebFlux controller:

```java
@GetMapping("/proxy/objects/{key}")
Mono<ResponseEntity<Flux<DataBuffer>>> proxy(
        @PathVariable String key,
        ObjectStoreClient client) {
    return client.downloadEntity(key);
}
```

The upstream status code and headers are forwarded to the caller without buffering the body.

---

## Memory behaviour

- Buffers are reference-counted by Reactor Netty and released as the consumer drains the `Flux`.
- Memory usage stays bounded regardless of payload size — the limit is the number of in-flight chunks, not the total payload.
- Standard observability (duration, response size from `Content-Length`) still applies to streaming responses.

---

## Combining streaming with other features

Streaming methods support all other annotations (`@PathVar`, `@QueryParam`, `@HeaderParam`, `@TimeoutMs`, `@Retry`, `@LogHttpExchange`, etc.) exactly like non-streaming methods.

```java
@GET("/exports/{format}")
@ApiName("export.download")
@TimeoutMs(0)   // disable per-request timeout for long downloads
Flux<DataBuffer> exportData(
        @PathVar("format") String format,
        @QueryParam("from") String from,
        @QueryParam("to") String to);
```

---

## Error handling for streaming responses

Errors from the upstream server (4xx / 5xx) are decoded and thrown before the `Flux<DataBuffer>` is emitted, following the same error-handling contract as non-streaming methods. See [03-error-handling.md](03-error-handling.md).
