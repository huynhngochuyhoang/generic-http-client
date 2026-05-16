# Roadmap V3 Execution Checklist

> Companion to [`ROADMAP_V3.md`](ROADMAP_V3.md). This file is the tracker; keep the rationale
> and design discussion in the roadmap. Check items off as they ship.

---

## Priority 1 — Transport confidence and startup diagnostics

### [ ] 3.1 HTTP/2 compatibility and TLS behavior tests
- [ ] Add an in-process Reactor Netty HTTP/2 server test.
- [ ] Verify an `http2-enabled: true` client succeeds against an HTTP/2 TLS endpoint.
- [ ] Verify default clients keep the existing HTTP/1.1 behavior.
- [ ] Keep tests network-free and independent of external services.

### [ ] 1.1 Configuration validation report at startup
- [ ] Emit one DEBUG startup summary per resolved client.
- [ ] Include base URL source, HTTP protocol, pool source, proxy/TLS, auth mode,
  resilience operators, observability, and logging flags.
- [ ] Redact all secrets and sensitive values.
- [ ] Fail fast for incomplete or contradictory proxy/TLS/auth settings.
- [ ] Add tests for redaction and at least three invalid config paths.

---

## Priority 2 — Default headers with validation

### [ ] 1.2 Declarative default headers per client
- [ ] Add `defaultHeaders` to `ReactiveHttpClientProperties.ClientConfig`.
- [ ] Add Spring configuration metadata for `reactive.http.clients.[name].default-headers`.
- [ ] Apply default headers to every request for the client.
- [ ] Ensure dynamic `@HeaderParam` values override configured defaults.
- [ ] Document YAML usage.

### [ ] 3.2 Guard against unsafe default header/query configuration
- [ ] Reuse existing header-name/header-value validation for configured default headers.
- [ ] Reject control characters in configured values.
- [ ] Cover invalid default headers in tests.
- [ ] Decide whether sensitive-looking configured keys should warn or fail.

---

## Priority 3 — Default query parameters and config precedence

### [ ] 1.3 Per-client default query parameters
- [ ] Add `defaultQueryParams` to `ReactiveHttpClientProperties.ClientConfig`.
- [ ] Add Spring configuration metadata for `reactive.http.clients.[name].default-query-params`.
- [ ] Apply defaults to every request for the client.
- [ ] Define and test merge behavior with method-level `@QueryParam`.
- [ ] Document no-query, existing-query, and override/append examples.

### [ ] 3.3 Configuration collision checks
- [ ] Document auth bean-name vs object-auth precedence.
- [ ] Add tests for auth precedence.
- [ ] Document risks of replacing the starter-managed connector in customizers.
- [ ] Fail fast or warn for ambiguous configuration combinations.
- [ ] Include precedence decisions in startup diagnostics where useful.

---

## Priority 4 — Error-category contract

### [ ] 3.4 Error-category consistency audit
- [ ] Document the published error-category mapping table.
- [ ] Add parameterized tests for HTTP status, decode, timeout, cancellation,
  DNS, connect, TLS, auth-provider, and resilience failures.
- [ ] Verify `ErrorCategories.from(Throwable)` agrees with observability tagging.
- [ ] Verify Micrometer and OpenTelemetry emit the same category names.
- [ ] Update test-helper assertions if any category semantics are clarified.

---

## Priority 5 — Documentation and metadata maintainability

### [ ] 2.1 Split README and reference docs by audience
- [ ] Keep README short and focused on product fit, install, and first use.
- [ ] Add `docs/16-production-checklist.md`.
- [ ] Add `docs/17-migration-from-webclient.md`.
- [ ] Include before/after examples for raw `WebClient` and Spring `@HttpExchange`.

### [ ] 2.4 Make generated configuration metadata the source of truth
- [ ] Add a metadata coverage test for important documented properties.
- [ ] Make missing metadata for new properties fail CI.
- [ ] Review defaults for every `reactive.http.*` key.
- [ ] Align high-value docs and metadata descriptions.

---

## Priority 6 — Safer exchange logging

### [ ] 1.4 Optional request/response logging presets
- [ ] Add `log-preset` property and metadata.
- [ ] Implement `metadata-only`, `headers`, and `bodies` presets.
- [ ] Keep the default conservative.
- [ ] Preserve sensitive-header redaction for all presets.
- [ ] Document how presets interact with body logging flags and custom loggers.

---

## Priority 7 — Naming polish and hot-path internals

### [ ] 2.2 Normalize client-name diagnostics
- [ ] Define and document allowed client-name characters.
- [ ] Validate client names during registration/property resolution.
- [ ] Ensure duplicate-name errors include both interface names.
- [ ] Normalize client-name usage across pool names, metrics tags, logs, spans,
  health output, and exception messages.

### [ ] 2.3 Reduce hot-path allocations in invocation handling
- [ ] Audit `ReactiveClientInvocationHandler` allocations on the steady path.
- [ ] Move stable decisions into cached metadata/request plans where it improves clarity.
- [ ] Capture before/after allocation notes in the implementation PR.
- [ ] Keep new caches bounded.
- [ ] Ensure behavior tests pass unchanged.

---

## Release Readiness

- [ ] `CHANGELOG.md` has V3 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README remains short and points to detailed docs.
- [ ] Configuration metadata is complete for all new properties.
- [ ] `mvn test` passes.
- [ ] Breaking behavior, if any, is explicitly called out before release.
