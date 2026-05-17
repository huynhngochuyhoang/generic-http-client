# Roadmap V4 Execution Checklist

> Companion to [`ROADMAP_V4.md`](ROADMAP_V4.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Documentation and test-helper ergonomics

### [ ] 2.2 Documentation as release artifact
- [ ] Add local documentation link validation.
- [ ] Check README and quick-start version snippets against `project.version`.
- [ ] Decide whether to generate a property reference from configuration metadata.
- [ ] If generated, add the generated reference and CI guard.

### [ ] 2.3 Test helper ergonomics
- [ ] Add fluent assertions for `RecordedExchange`.
- [ ] Cover method, path, query, headers, body, and status assertions.
- [ ] Add helpers for repeated query params and redacted headers.
- [ ] Document common success and failure examples.

---

## Priority 2 — Conflict and cardinality guardrails

### [ ] 3.1 Annotation and configuration conflict audit
- [ ] Document precedence for annotations, `@ApiRef`, defaults, auth, customizers,
  resilience, and logging.
- [ ] Add tests for each documented precedence path.
- [ ] Fail fast or document behavior for any remaining ambiguity.

### [ ] 3.2 Observability cardinality guardrails
- [ ] Audit Micrometer tags and OpenTelemetry attributes for high-cardinality risk.
- [ ] Keep risky dimensions opt-in.
- [ ] Add warnings or metadata guidance for risky settings.
- [ ] Add tests for conservative defaults.

---

## Priority 3 — Invocation internals

### [ ] 2.1 Request plan model
- [ ] Introduce an immutable request plan where it simplifies metadata use.
- [ ] Move stable request decisions out of the per-call path.
- [ ] Keep dynamic argument resolution explicit.
- [ ] Capture allocation notes in the implementation PR.
- [ ] Ensure behavior tests pass unchanged.

---

## Priority 4 — Lifecycle and error mapping extensions

### [ ] 1.1 Client lifecycle hooks
- [ ] Add a small lifecycle SPI for start, success, error, cancellation, and retry
  attempt boundaries.
- [ ] Define ordering and failure isolation.
- [ ] Add tests for multiple hook beans.
- [ ] Document hooks vs customizers vs observers.

### [ ] 1.2 Declarative response error decoding policy
- [ ] Add per-client structured error body mapper support.
- [ ] Preserve status, response body, and `ErrorCategory`.
- [ ] Fall back to the existing default decoder when no mapper applies.
- [ ] Add mapper hit, miss, invalid-body, and fallback tests.

---

## Release Readiness

- [ ] `CHANGELOG.md` has V4 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README remains short and links to detailed docs.
- [ ] New properties have configuration metadata and metadata tests.
- [ ] `mvn test` passes.
- [ ] Breaking behavior, if any, is explicitly called out before release.
