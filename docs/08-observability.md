# Observability

The starter ships two observability back-ends that are mutually exclusive: Micrometer (default) and OpenTelemetry (optional companion module). Both implement the `HttpClientObserver` extension point.

---

## Micrometer metrics

When a `MeterRegistry` bean is present, `MicrometerHttpClientObserver` records four meters per exchange.

### `reactive.http.client.requests` (Timer)

End-to-end duration from first attempt to final completion (after all retries).

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `http.status_code` | Numeric HTTP status code, or `NONE` on network failure |
| `outcome` | `SUCCESS`, `REDIRECTION`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN` |
| `exception` | Simple class name of the thrown exception, or `none` |
| `error.category` | `ErrorCategory` value — see [03-error-handling.md](03-error-handling.md) |
| `uri` | Path template (e.g. `/users/{id}`), or `NONE`; disable with `include-url-path: false` |

### `reactive.http.client.requests.attempts` (DistributionSummary)

Number of subscription attempts per invocation. `1` = succeeded on first try; `>1` = Resilience4j retry fired. A p95 above `1` signals degradation in a downstream service.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.request.size` (DistributionSummary)

Serialized request body bytes. Recorded only for cheaply measurable types: `byte[]`, `String`, or `null` (`0`). POJO bodies are not measured to avoid double-serialization cost.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.response.size` (DistributionSummary)

Response body bytes as advertised by `Content-Length`. Chunked responses and those without the header are skipped.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.latency` (Timer with SLO histogram) *(opt-in)*

A separate latency Timer configured with `serviceLevelObjectives(...)` buckets, enabling P99/SLO-style analysis in Prometheus/Grafana without tag-cardinality explosion.  Disabled by default — enable via configuration.

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `uri` | Path template (e.g. `/users/{id}`), or `NONE` |

> The histogram deliberately omits `http.status_code`, `outcome`, `exception`, and `error.category` to keep label-set cardinality low and avoid Prometheus time-series explosion.

---

## Observability configuration

```yaml
reactive:
  http:
    observability:
      enabled: true
      metric-name: reactive.http.client.requests   # custom timer/counter name
      include-url-path: true              # set false for high-cardinality paths
      log-request-body: false             # include body in span events (PII risk)
      log-response-body: false
      histogram:
        enabled: false                    # opt-in latency histogram (SLO buckets)
        slo-boundaries-ms: [50, 100, 200, 500, 1000, 2000, 5000]
```

> **Production recommendation:** enable body logging only when truly required, and always apply PII masking before the data leaves your network boundary.

---

## Actuator health indicator

When `spring-boot-starter-actuator` is on the classpath and a `MeterRegistry` bean is present, the starter auto-registers `HttpClientHealthIndicator`. It reads the `reactive.http.client.requests` timer and reports per-client error rates computed from probe-to-probe deltas.

```yaml
reactive:
  http:
    observability:
      health:
        enabled: true              # master switch (default true)
        error-rate-threshold: 0.5  # ratio above which a client reports DOWN
        min-samples: 10            # delta count required before evaluating a client
```

### Status logic

| Condition | Status |
|---|---|
| `delta-count < min-samples` | `INSUFFICIENT_SAMPLES` (no DOWN reported) |
| `errorRate <= error-rate-threshold` | `UP` |
| `errorRate > error-rate-threshold` | `DOWN` — overall indicator is `DOWN` |

### Sample actuator response

```json
{
  "status": "DOWN",
  "details": {
    "user-service":    { "samples": 10, "errors": 8, "errorRate": 0.8, "status": "DOWN" },
    "partner-service": { "samples": 20, "errors": 1, "errorRate": 0.05, "status": "UP" },
    "errorRateThreshold": 0.5,
    "minSamples": 10
  }
}
```

To override the indicator, register your own bean named `reactiveHttpClientHealthIndicator`.

---

## Connection-pool metrics

Enable Reactor Netty pool gauges per client or globally:

```yaml
reactive:
  http:
    network:
      connection-pool:
        metrics-enabled: true   # global
    clients:
      user-service:
        pool:
          metrics-enabled: true   # per-client
```

Gauges published:

| Gauge | Description |
|---|---|
| `reactor.netty.connection.provider.total.connections` | All connections |
| `reactor.netty.connection.provider.active.connections` | Connections in use |
| `reactor.netty.connection.provider.idle.connections` | Available idle connections |
| `reactor.netty.connection.provider.pending.connections` | Callers waiting for a connection |

All gauges are tagged with `name = reactive-http-client-<clientName>`.

---

## OpenTelemetry tracing (`reactive-http-client-otel`)

The optional OTel companion records each outbound exchange as a span using the [HTTP client semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/).

### Add the dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-otel</artifactId>
  <version>${reactive-http-client.version}</version>
</dependency>
```

### Activation

When `opentelemetry-api` is on the classpath and an `OpenTelemetry` bean is present, the auto-configuration registers `OpenTelemetryHttpClientObserver`. Disable without removing the dependency:

```yaml
reactive:
  http:
    observability:
      otel:
        enabled: false
```

### Span fields

| Field | Source |
|---|---|
| Span name | `<METHOD> <api.name>` — e.g. `GET getUserById` |
| Span kind | `CLIENT` |
| `http.request.method` | HTTP verb |
| `http.response.status_code` | Response status code |
| `url.template` | Path template, e.g. `/users/{id}` |
| `error.type` | `ErrorCategory` name; falls back to the exception's simple class name |
| `rhttp.client.name` | Logical client name |
| `rhttp.api.name` | `@ApiName` value or method name |
| `rhttp.attempt.count` | Total attempts (>1 = retried) |
| `rhttp.request.bytes` | Request body bytes (when measurable) |
| `rhttp.response.bytes` | Response body bytes (from `Content-Length`) |

Errors set `StatusCode.ERROR` and call `recordException(...)` so the exception event appears in the span.

### Mutual exclusion with Micrometer

`OpenTelemetryHttpClientObserver` registers under `@ConditionalOnMissingBean(HttpClientObserver.class)`, which means pulling in the OTel module disables the Micrometer observer. To run both, register a composite `HttpClientObserver` bean:

```java
@Bean
HttpClientObserver compositeObserver(
        MicrometerHttpClientObserver micrometer,
        OpenTelemetryHttpClientObserver otel) {
    return event -> {
        micrometer.onExchange(event);
        otel.onExchange(event);
    };
}
```

---

## Resilience4j metrics

See [07-resilience4j.md](07-resilience4j.md) for details on the auto-bound Resilience4j meters.
