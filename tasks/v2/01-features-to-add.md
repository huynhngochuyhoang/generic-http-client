# V2 Features To Add

Roadmap bucket: `1. Features to add`.

## Tasks

- [ ] **1.1 AWS SigV4 auth provider**
  - Implement `AwsSigV4AuthProvider`.
  - Validate against AWS reference vectors.
  - Document SigV4 and SigV4a scope.
- [ ] **1.2 Property-driven auth-provider configuration**
  - Add `AuthProviderFactory` SPI.
  - Support object-style auth config while preserving bean-name string form.
  - Add metadata and binding tests.
- [ ] **1.3 Composite observer support**
  - Allow Micrometer metrics and OTel spans to run together.
  - Define custom-observer override semantics.
  - Add combined metrics + tracing regression tests.
- [ ] **1.4 JUnit 5 `@MockHttpServer` extension**
  - Add annotation + extension.
  - Decide artifact/dependency boundary.
  - Add docs and consuming tests.
- [ ] **1.5 Resilience4j rate-limiter support**
  - Add optional rate-limiter operator support.
  - Add method-level and client-level configuration.
  - Document operator ordering.

## Release Notes

- Most items can ship in minor releases.
- Composite observer support may need `2.0.0` if existing observer replacement semantics are treated as a public contract.
