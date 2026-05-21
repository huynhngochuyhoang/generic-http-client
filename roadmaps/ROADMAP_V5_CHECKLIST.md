# Roadmap V5 Execution Checklist

> Companion to [`ROADMAP_V5.md`](ROADMAP_V5.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Correctness guardrails

### [x] 3.1 URI encoding and template edge-case audit
- [x] Cover reserved characters in `@PathVar` and `@QueryParam`.
- [x] Cover default query values combined with method values and existing query
  strings.
- [x] Cover `@ApiRef` paths with templates and query strings.
- [x] Document whether callers pass raw or pre-encoded values.

### [x] 3.3 Validation for numeric configuration bounds
- [x] Audit timeout, pool, codec, histogram, and health-threshold properties.
- [x] Add validation for negative durations and impossible thresholds.
- [x] Preserve documented `0 = disabled/default` behavior.
- [x] Ensure validation messages include the property name and accepted range.

---

## Priority 2 — Reactive body safety

### [ ] 3.2 Body consumption and cancellation safety
- [ ] Test cancellation before response and during response body read.
- [ ] Verify lifecycle hooks and observers receive one terminal signal.
- [ ] Verify mapper fallback does not consume the body twice.
- [ ] Verify streaming responses do not buffer accidentally.

---

## Priority 3 — Configuration clarity

### [ ] 2.1 Move request timeout configuration out of resilience
- [ ] Add the new canonical client request-timeout property.
- [ ] Keep `resilience.timeout-ms` as a deprecated alias for one compatibility
  cycle.
- [ ] Define conflict behavior when both properties are configured.
- [ ] Update metadata, timeout docs, and guardrail docs.

### [ ] 2.2 Auto-configuration and override contract audit
- [ ] Add context-runner tests for optional dependency combinations.
- [ ] Add tests for user-supplied observer/auth/customizer override paths.
- [ ] Document named built-in beans that users may override.
- [ ] Verify disabled properties suppress expected beans.

---

## Priority 4 — HTTP contract ergonomics

### [ ] 1.2 Non-streaming response envelope support
- [ ] Support `Mono<ResponseEntity<T>>` for successful responses.
- [ ] Support `Mono<ResponseEntity<Void>>`.
- [ ] Preserve existing non-2xx error decoder and mapper behavior.
- [ ] Document response-envelope usage.

### [ ] 1.3 Built-in Problem Detail error mapping
- [ ] Add opt-in mapper for `application/problem+json`.
- [ ] Preserve status, raw body, and `ErrorCategory`.
- [ ] Test valid, invalid, missing-content-type, `4xx`, and `5xx` cases.
- [ ] Document application handling examples.

---

## Priority 5 — Release compatibility

### [ ] 1.1 Spring AOT and native-image readiness
- [ ] Add runtime hints for client proxies, annotations, and configuration
  properties.
- [ ] Add an AOT smoke test application.
- [ ] Document supported native-image paths and limits.

### [ ] 2.3 Compatibility smoke matrix
- [ ] Add a release smoke job or profile.
- [ ] Exercise a minimal declarative client with metrics enabled.
- [ ] Capture tested Java and framework versions in release docs.

---

## Release Readiness

- [ ] `CHANGELOG.md` has V5 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README stays short and links to detailed docs.
- [ ] New properties have configuration metadata and metadata tests.
- [ ] New public APIs have focused tests and concise docs.
- [ ] `mvn test` passes.
- [ ] Breaking behavior, if any, is explicitly called out before release.
