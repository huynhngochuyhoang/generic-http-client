# V2 Features To Optimize

Roadmap bucket: `2. Features to optimize`.

## Tasks

- [ ] **2.1 OTel `server.address` and `server.port` attributes**
  - Expand `HttpClientObserverEvent` carefully.
  - Emit OTel semantic attributes.
  - Guard Micrometer host/port tags behind cardinality opt-in.
- [ ] **2.2 Precompute immutable request plans per method**
  - Move stable invocation decisions out of the hot path.
  - Keep dynamic argument resolution separate.
  - Preserve `@ApiRef` diagnostics.
- [ ] **2.3 Improve diagnostics for auto-configured filter order**
  - DEBUG-log applied `WebClientCustomizer` classes per client.
  - Document built-in filter order.
  - Test OTel/header preservation and customizer ordering.
- [ ] **2.4 Documentation and examples package**
  - Add OAuth2, Resilience4j, OTel propagation, multipart, streaming, and test-helper examples.
  - Decide whether examples compile in CI.

## Verification

- Run `mvn test`.
- For hot-path changes, compare allocation/complexity informally before and after; add microbenchmarks only if a change is performance-sensitive enough to justify ongoing maintenance.
