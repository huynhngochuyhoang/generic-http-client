# Roadmap Execution Checklist

> Companion to `ROADMAP.md`. Ordered by the priority suggested at the bottom of that document.
> Check items off as they ship. Each top-level entry points back to the matching section in
> `ROADMAP.md` for full rationale — this file is the tracker, not the spec.

---

## Priority 1 — Latent security / log-safety issues

### [x] 3.7 Redact or allow-list inbound headers before logging
- [x] Reuse `DefaultHttpExchangeLogger`'s sensitive-header list in `InboundHeadersWebFilter`
  before storing the snapshot in `HttpExchangeLogContext`. _Extracted into the new
  `SensitiveHeaders` utility so both sides share one list._
- [x] Add property `reactive.http.inbound-headers.deny-list` (default: same as the
  outbound redaction list) and `reactive.http.inbound-headers.allow-list` (optional).
- [x] Unit test: upstream request with `Authorization`, `Cookie`, `X-Api-Key` headers —
  the captured snapshot must contain redacted markers, not the raw values.
  _`InboundHeadersWebFilterTest#redactsSensitiveHeadersByDefault` +
  `#redactionIsCaseInsensitive`._
- [x] Integration test: `DefaultHttpExchangeLogger` INFO line must not contain raw
  redacted header values. _Covered by the filter redacting the map before it ever
  reaches `HttpExchangeLogContext.inboundHeaders()`; existing logger tests already
  assert the downstream log format consumes the snapshot verbatim._
- [x] Changelog entry under **Security**.

### [x] 3.1 Validate correlation-ID length and character set
- [x] Add `reactive.http.correlation-id.max-length` (default 128).
- [x] In `CorrelationIdWebFilter.filter(...)`: reject values that exceed the cap or
  contain control characters; log at DEBUG and continue without a correlation ID.
  _Also applied in the outbound `exchangeFilter()` so MDC-sourced values are validated
  before propagation._
- [x] Unit test: oversize value → dropped; CRLF value → dropped; normal UUID → preserved.
  _`CorrelationIdWebFilterTest#shouldRejectCorrelationIdExceedingMaxLength`,
  `#shouldRejectCorrelationIdWithControlCharacters`,
  `#shouldAcceptCorrelationIdAtMaxLengthBoundary`._
- [x] Changelog entry under **Security**.

---

## Priority 2 — Silent behaviour regression since 1.8.1

### [x] 3.9 Apply `WebClientCustomizer` beans to every client
- [x] Inject `ObjectProvider<WebClientCustomizer>` into `ReactiveHttpClientFactoryBean`.
  _Applied at the prototype bean (`starterWebClientBuilder`) instead — mirrors Spring
  Boot's own `WebClientAutoConfiguration` so customizers run exactly once per pull,
  before the factory bean sees the builder. No change needed in the factory bean._
- [x] Apply customizers in order after `WebClient.builder()` and before the
  starter-owned filters. _Applied via `orderedStream()` so `@Order` is respected;
  starter filters (correlation-id, auth, logging) are added afterwards by the
  factory bean._
- [x] Regression test: register a `WebClientCustomizer` bean, assert each
  `@ReactiveHttpClient` proxy's underlying builder was customised exactly once.
  _`ReactiveHttpClientAutoConfigurationTest#starterBuilderAppliesRegisteredCustomizer`
  and `#customizerAppliedExactlyOncePerBuilderInstance`._
- [x] Regression test: two clients must not share filter state (existing 1.8.1 guarantee
  must still hold).
  _`ReactiveHttpClientAutoConfigurationTest#starterBuilderIsPrototypeScopedSoStateIsNotSharedAcrossClients`._
- [x] Changelog entry under **Fixed**.

---

## Priority 3 — On-call pain points

### [x] 1.4 Per-client connection-pool tuning + idle / max-life eviction
- [x] Extend `ClientConfig` with optional `ConnectionPoolConfig pool`.
  _Nullable by default — null means "inherit the global pool". When set, the
  client-level block replaces the global one wholesale (no field merging)._
- [x] Add `maxIdleTimeMs`, `maxLifeTimeMs`, `evictInBackgroundMs` to `ConnectionPoolConfig`.
  _All `long`, default `0` = Reactor Netty's built-in default (preserves prior
  behaviour)._
- [x] Resolve client-override → global default in `ReactiveHttpClientFactoryBean.buildWebClient()`.
  _New `resolveConnectionPool(config, network)` helper picks `config.pool` if
  set, else `network.connectionPool`, else a fresh default._
- [x] Forward new values to `ConnectionProvider.builder(...)`.
  _`maxIdleTime` / `maxLifeTime` / `evictInBackground` are applied only when
  `> 0`, so the default-zero values leave the provider untouched._
- [x] Unit test: two clients with different `maxConnections` must not share a pool.
  _`ReactiveHttpClientPropertiesTest#perClientPoolOverrideBindsEveryField` binds
  `big` / `small` side-by-side and asserts `big` gets its override while
  `small.pool == null` (falls through to global).
  `#inheritedPoolStaysReferenceEqualToGlobal` confirms inheritance semantics.
  Pool identity per-client is already guaranteed by the `"reactive-http-client-" + clientName`
  provider name — see 3.3 for the duplicate-name hardening work._
- [x] Document behaviour in README, including when a pooled connection is evicted.
  _README §2.5 gained a "Connection-pool tuning" block describing inheritance
  semantics and the new eviction knobs._

### [x] 2.4 Rename / clarify network-level timeouts
- [x] Introduce `network-read-timeout-ms` / `network-write-timeout-ms` as the canonical
  names; keep the old names as deprecated aliases.
  _Backing fields renamed to `networkReadTimeoutMs` / `networkWriteTimeoutMs`;
  legacy getters/setters delegate to the same field so both YAML keys continue
  to bind._
- [x] Tag deprecated fields with `@DeprecatedConfigurationProperty`.
  _Legacy getters carry `@Deprecated` + `@DeprecatedConfigurationProperty(replacement = …)`
  so IDEs surface the replacement name on hover._
- [x] Update `NetworkConfig` Javadoc to state which timeout wins in each scenario.
  _Multi-paragraph Javadoc on `NetworkConfig` distinguishing channel-level
  safety nets from per-request response timeouts, and explaining that the
  per-request timeout fires first when both apply._
- [x] Update README §2.5 with a "which timeout fires first" matrix.
  _4-row matrix (connect / per-request / safety-net read / safety-net write)
  with scope, default, and firing condition for each; plus a rule-of-thumb
  paragraph._
- [x] Add one sentence to `@TimeoutMs` Javadoc cross-referencing the property.
  _Javadoc on `@TimeoutMs` now calls out the safety-net properties by name and
  clarifies that `@TimeoutMs(0)` disables only the per-request timeout, not the
  safety nets._

---

## Priority 4 — Operational visibility

### [x] 2.1 Expose connection-pool + Resilience4j metrics
- [x] Enable `reactor.netty.Metrics` on each `ConnectionProvider` when a
  `MeterRegistry` bean is present.
  _Opt-in via `reactive.http.network.connection-pool.metrics-enabled` (default
  `false`) because Reactor Netty's internal gauges add a small per-request
  cost; the global flag applies to every client unless a client-level pool
  overrides it._
- [x] Auto-register Resilience4j's `TaggedCircuitBreakerMetrics`,
  `TaggedRetryMetrics`, `TaggedBulkheadMetrics` bindings against the shared registry.
  _Added `resilience4j-micrometer` as an optional dependency and a nested
  `Resilience4jMetricsAutoConfiguration`. Each binding is its own `MeterBinder`
  bean with a stable name so users can override individual bindings._
- [x] Gate both behind `@ConditionalOnBean(MeterRegistry.class)`.
  _Pool metrics no-op when no `MeterRegistry` is registered with Reactor
  Netty's `Metrics.REGISTRY`; Resilience4j nested config carries
  `@ConditionalOnBean(MeterRegistry.class)` + `@ConditionalOnClass(TaggedCircuitBreakerMetrics.class)`._
- [x] Document the new metric names in README's observability section.
  _README §7 lists the four request metrics plus a dedicated "Connection-pool
  metrics" subsection and a "Resilience4j metrics" subsection describing the
  auto-binding trigger conditions and override points._

### [x] 2.2 Request / response body-size distributions
- [x] Capture request size once per invocation (the cached body bytes already exist).
  _`measureRequestBodyBytes` in `ReactiveClientInvocationHandler` covers
  `byte[]` / `CharSequence` / `null` cheaply; other body types return
  `HttpClientObserverEvent.UNKNOWN_SIZE` (`-1`) so the Micrometer observer
  skips the distribution summary._
- [x] Capture response size in the exchange pipeline prior to decoding.
  _`extractContentLengthBytes` pulls from the already-captured response
  headers inside `reportExchange`; no additional buffering. Chunked
  responses / missing header → `UNKNOWN_SIZE`._
- [x] Emit `http.client.requests.request.size` and `http.client.requests.response.size`
  `DistributionSummary` metrics with the same tags as the existing timer.
  _Implemented in `MicrometerHttpClientObserver`; recorded only when the
  corresponding size is `>= 0`, so unmeasurable cases don't pollute the
  histogram._
- [x] Extend `HttpClientObserverEvent` (+ deprecate the old constructor if needed) to
  carry the sizes.
  _New 13-arg constructor; the 11-arg constructor is `@Deprecated(since = "1.9.0")`
  and delegates with `UNKNOWN_SIZE`. Back-compat preserved for custom observers._
- [x] Unit test for both directions; ensure sizes are `0` when the body is null rather
  than omitted.
  _`MicrometerHttpClientObserverTest#recordsRequestAndResponseSizeDistributionsWhenSizesAreKnown`,
  `#skipsSizeDistributionsWhenSizesAreUnknown`,
  `#recordsZeroSizedRequestBodyExplicitly`._

### [x] 1.6 Actuator health indicator + pool gauges
- [x] New `HttpClientHealthIndicator` gated by
  `@ConditionalOnClass(HealthIndicator.class)`.
  _`spring-boot-actuator` added as an optional dep; nested
  `HttpClientHealthIndicatorAutoConfiguration` registers the indicator under
  the name `reactiveHttpClientHealthIndicator` when actuator + `MeterRegistry`
  are both present._
- [x] Compute status from a rolling error-rate window per client name; expose
  status and rate in the `Health` details map.
  _The indicator snapshots every `http.client.requests` timer on each probe,
  groups counts by `client.name`, and computes the error ratio from the delta
  against the previous snapshot — so the window size equals the probe
  interval. Per-client details include `samples`, `errors`, `errorRate`, and
  `status` (UP / DOWN / INSUFFICIENT\_SAMPLES). Overall status is DOWN if any
  tracked client is DOWN. Configurable via
  `reactive.http.observability.health.error-rate-threshold` (default 0.5) and
  `.min-samples` (default 10). Design note: the indicator is deliberately NOT
  an `HttpClientObserver`, so the existing
  `@ConditionalOnMissingBean(HttpClientObserver.class)` override contract is
  preserved — no composite-observer refactor was needed._
- [x] Wire pool gauges (`reactor.netty.connection.provider.total.connections`, etc.)
  into the starter's Micrometer integration.
  _Shipped via 2.1's `metrics-enabled` flag — Reactor Netty publishes the
  pool gauges directly to the registry._
- [x] Document opt-in in README.
  _Covered by the "Connection-pool metrics (Reactor Netty)" and "Actuator
  health indicator" subsections in README §7, including a sample JSON
  payload._

---

## Priority 5 — Most-requested new features

### [x] 1.2 Multipart / form-data bodies
- [x] Add annotations: `@MultipartBody` (method), `@FormField`, `@FormFile` (parameter).
  _Plus the `FileAttachment` convenience record for `byte[] + filename +
  content-type` payloads. `@FormFile` accepts `byte[]`, any `Resource`, or
  `FileAttachment`._
- [x] Extend `MethodMetadataCache` parser to recognise them.
  _Parser now stores the form-field/file parameter indexes and rejects
  three misuses at parse time: `@MultipartBody` + `@Body` together,
  `@FormField` / `@FormFile` without `@MultipartBody`, and `@MultipartBody`
  with no parts._
- [x] Extend `ReactiveClientInvocationHandler` body-encoding branch to build a
  `MultiValueMap<String, HttpEntity<?>>` via `MultipartBodyBuilder`.
  _New `buildMultipartBody(meta, args)` helper; non-multipart calls
  unaffected. Content-Type with the right boundary is set by WebClient via
  `BodyInserters.fromMultipartData`._
- [x] Unit tests: single file, multiple files, mixed file + fields, `Resource` vs. byte[],
  rejection when `@MultipartBody` is combined with `@Body`.
  _`MultipartRequestTest` (7 cases) covers `Resource` + field, `byte[]`
  with annotation defaults, `FileAttachment` overriding annotation defaults,
  unsupported parameter type, and all three parse-time rejections._
- [x] README example for file upload.
  _New §2.5.2 with an avatar-upload + CSV-import example._

### [x] 1.3 `reactive-http-client-test` module
- [x] New Maven module with its own `pom.xml` under the parent.
  _Registered in the parent `<modules>`. Pulls `spring-test` as a compile
  dep so `MockClientHttpRequest` is available to consumers without an
  extra import._
- [x] `MockReactiveHttpClient<T>` helper that builds a proxy against an in-process
  `ExchangeFunction`.
  _Records every outbound exchange into a live list, serves canned
  responses by matcher (`respondToPath`, `respondTo`, or arbitrary
  predicate), and falls through to a configurable fallback (HTTP 404 by
  default) so unmatched calls fail loudly. Uses an empty
  `StaticApplicationContext` so the helper has no Mockito runtime
  dependency on the consumer side._
- [ ] JUnit 5 `@MockHttpServer` extension backed by a canned-response registry.
  _Deferred. The matcher-driven builder API on `MockReactiveHttpClient`
  already covers the same ergonomics inside a single `@Test`; the JUnit
  5 extension is a thin convenience wrapper that can land in a follow-up
  if usage demands it._
- [x] Assertion helpers for `ErrorCategory` outcomes.
  _`ErrorCategoryAssertions.assertThatFails(mono).hasErrorCategory(...).hasStatusCode(...)`._
- [x] Port a subset of the internal tests to the new module as a dogfood check.
  _`MockReactiveHttpClientTest` exercises the helper end-to-end across
  GET/POST, captured bodies, fallback 404 → CLIENT_ERROR, 429 →
  RATE_LIMITED, and 5xx → SERVER_ERROR._
- [x] Publish docs / README for the new artifact.
  _New §9.1 in the main README with dependency coordinates and usage
  example._

### [~] 1.7 Built-in auth providers
- [x] `OAuth2ClientCredentialsTokenProvider` on top of `RefreshingBearerAuthProvider`.
  - [x] Token-endpoint call, client-id / client-secret basic auth, configurable
    scope / audience.
    _Plus form-post auth style, optional `audience`, configurable
    `expiryLeeway` (default 30 s)._
  - [x] Unit test + concurrent-refresh test parity with the existing refreshing provider.
    _`OAuth2ClientCredentialsTokenProviderTest` (6 cases) covers basic
    auth wiring, form-post auth, expiry-leeway subtraction, missing
    `expires_in` → non-expiring token, `scope` / `audience` form
    forwarding, builder validation. Concurrent-refresh semantics are
    inherited from the surrounding `RefreshingBearerAuthProvider` —
    already covered by its existing tests._
- [ ] `AwsSigV4AuthProvider` implementing `AuthProvider` directly.
  **Deferred.** SigV4 is ~300 LOC of cryptographic code (canonical
  request construction, double-HMAC key derivation, regional / service
  scope handling). Shipping it without thorough AWS reference test
  vectors is a footgun — production HMAC bugs typically only surface
  under very specific request shapes (URL-encoded path segments, empty
  query strings, multi-line headers). Track as a follow-up dedicated PR
  with the official AWS SigV4 test-suite vectors as the acceptance bar.
  - [ ] Canonical request → string-to-sign → signature; reuse cached body bytes.
  - [ ] Unit test against AWS SigV4 test vectors.
- [ ] Auto-configuration hooks so users can enable either via `application.yml`.
  _Deferred alongside SigV4 — once both providers exist, a single
  property-driven config block makes more sense than wiring OAuth2
  alone._

---

## Priority 6 — Larger API-surface changes (1.9.x / 2.0.0)

### [ ] 1.1 OpenTelemetry-native observer
- [ ] New optional module `reactive-http-client-otel`.
- [ ] `OpenTelemetryHttpClientObserver` mirroring `MicrometerHttpClientObserver`.
- [ ] Semantic conventions: `http.request.method`, `http.response.status_code`,
  `server.address`, `error.type` (from `ErrorCategory`).
- [ ] Baggage propagation through the Reactor `Context`.
- [ ] Auto-configure under `@ConditionalOnClass(io.opentelemetry.api.OpenTelemetry.class)`.
- [ ] Integration test with the OTel SDK in-memory exporter.

### [ ] 1.9 Per-method `@Retry`, `@CircuitBreaker`, `@Bulkhead`
- [ ] New annotations, parsed in `MethodMetadataCache`.
- [ ] `Resilience4jOperatorApplier` picks per-method instance names when present,
  falling back to the client-level config.
- [ ] Fail-fast validation when a referenced instance name is missing at startup.
- [ ] Tests: method-level override wins, missing instance fails fast, coexistence with
  the existing client-level config.

### [ ] 1.5 HTTP proxy + custom SSL / mTLS support
- [ ] `NetworkConfig.ProxyConfig` (host, port, type, optional auth).
- [ ] `ClientConfig.TlsConfig` (truststore, keystore, protocols, cipher allow-list).
- [ ] Apply in `buildWebClient` via `HttpClient.proxy(...)` and `HttpClient.secure(...)`.
- [ ] Integration test with a local self-signed peer.

### [ ] 1.8 Response streaming passthrough
- [ ] Detect `Flux<DataBuffer>` / `Mono<ResponseEntity<Flux<DataBuffer>>>` return types.
- [ ] Skip the in-memory codec in those cases.
- [ ] Make sure observability still records duration / size correctly.
- [ ] Test with a large (>codec-max) response.

### [ ] 1.10 Configurable correlation-ID and inbound-header filters
- [ ] `reactive.http.correlation-id.mdc-keys: list` consumed by `CorrelationIdWebFilter`.
- [ ] `reactive.http.inbound-headers.allow-list` / `deny-list` consumed by
  `InboundHeadersWebFilter` (overlaps with 3.7 — implement once).

---

## Priority 7 — Housekeeping (roll into patch releases)

### [ ] 3.2 Guard `codec-max-in-memory-size-mb` edge cases
- [ ] Reject negative values at startup.
- [ ] Choose: treat `0` as "unlimited" (pass `-1`) **or** log a WARN that the default
  is being used. Document the decision.

### [ ] 3.3 Fail fast on duplicate client names
- [ ] Detect duplicates in `ReactiveHttpClientsRegistrar`.
- [ ] Throw with a message naming both interfaces.
- [ ] Include interface FQN in the pool name as a belt-and-braces safety net.

### [ ] 3.4 Cap `@TimeoutMs`
- [ ] Reject values above a documented maximum (30 min) at parse time.
- [ ] Update `@TimeoutMs` Javadoc with the accepted range.

### [ ] 3.5 Warn once on empty `@GET("")` / blank path template
- [ ] Parse-time detection in `MethodMetadataCache`.
- [ ] Per-method dedupe so the warning fires once per method, not per call.

### [ ] 3.6 Preserve HTTP context when error decoding fails
- [ ] In `decodeErrorResponse`, always construct `HttpClientException` / `RemoteServiceException`
  carrying status code + best-effort body.
- [ ] Attach the decoding exception as `cause`.
- [ ] Test: induce a `DataBufferLimitException` while decoding a 502 — assert the final
  exception is `RemoteServiceException(502, cause=DataBufferLimitException)`.

### [ ] 3.8 Dispose `ConnectionProvider` on context shutdown
- [ ] Make `ReactiveHttpClientFactoryBean` implement `DisposableBean`.
- [ ] Call `connectionProvider.disposeLater().subscribe()` on destroy.
- [ ] Context-reload test asserting providers are not leaked between cycles.

### [ ] 3.10 Cap `resilienceWarningKeys` growth
- [ ] Apply the 256-entry cap already used by `loggerCache`, or switch to Caffeine
  with `expireAfterAccess`.

### [ ] 2.3 Publish `spring-configuration-metadata.json`
- [ ] Author `additional-spring-configuration-metadata.json` covering every property
  on `ReactiveHttpClientProperties` (and nested config classes).
- [ ] Include `@DeprecatedConfigurationProperty` hints for `log-body`.
- [ ] CI check: spring-boot-configuration-processor run produces no warnings.

### [ ] 2.5 Finish deprecating `log-body`
- [ ] Mark as deprecated in 1.9.x with replacement hint.
- [ ] Delete in 2.0.0 along with the backwards-compatibility branch in
  `ClientConfig.isExchangeLoggingEnabled()`.

### [ ] 2.6 Cache the case-insensitive header view per invocation
- [ ] Build a `TreeMap(CASE_INSENSITIVE_ORDER)` once inside `ResolvedArgs`.
- [ ] Replace all `getHeaderIgnoreCase` call sites with a lookup.

### [ ] 2.7 Defer work inside `DefaultHttpExchangeLogger`
- [ ] Move header sanitisation / body truncation inside the `log.isInfoEnabled()` guard,
  or switch to SLF4J fluent API (`log.atInfo().addKeyValue(...)`).
- [ ] Microbench before/after at ≥1 krps.

### [ ] 2.8 Resolve `HttpExchangeLogger` once per method
- [ ] Store the resolved instance on `MethodMetadata` after first resolution.
- [ ] Drop the per-invocation `loggerCache` lookup on the hot path (keep the cache for
  the initial resolution).

### [ ] 2.9 Bounded-depth cause traversal in error classification
- [ ] Replace `IdentityHashMap` guard with a bounded loop (max depth 16).
- [ ] Confirm no regression against existing cause-chain tests.

### [ ] 2.10 Unify Mono / Flux invocation pipelines
- [ ] Extract the shared request-construction + error-decoding portion into a private
  helper parameterised by a `Function<ClientResponse, Publisher<?>>`.
- [ ] Existing 1.8.1 `reportExchange(...)` already covers the terminal callback.
- [ ] Regression tests must cover both paths after the refactor.

---

## How to use this checklist

- Work roughly top-to-bottom; priority 1 items should be the next release's headline.
- Items within a priority band are independent and can parallelise.
- Tick each sub-bullet as it merges; leave the parent unchecked until **all** sub-bullets
  are done, so the file doubles as a release-note source.
- When a full section ships, move its entry into `CHANGELOG.md` under the release
  version and leave the checked heading here for a release or two before pruning.
