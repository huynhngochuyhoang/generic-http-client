# Observability Deepening

Roadmap items: `2.1 OTel server.address and server.port attributes`, `1.3 Composite observer support`.

Suggested release: `1.15.0+` if backward-compatible; hold composite observer semantics for `2.0.0` if the observer override contract is treated as breaking.

## Scope

- Add `serverAddress` and `serverPort` to `HttpClientObserverEvent`.
- Populate host/port from the resolved outbound request URL.
- Emit OTel `server.address` and `server.port` span attributes.
- Optionally expose Micrometer `server.address` / `server.port` tags behind a cardinality opt-in.
- Add `CompositeHttpClientObserver` so Micrometer metrics and OTel spans can run together without user-written delegation.

## Acceptance

- [ ] Existing `HttpClientObserverEvent` constructor compatibility is preserved or explicitly migrated.
- [ ] OTel tests assert `server.address` and `server.port`.
- [ ] Micrometer tests cover the cardinality opt-in flag.
- [ ] Combined Micrometer + OTel test records both a timer and a CLIENT span for one exchange.
- [ ] User-supplied observer behavior is documented and tested.
- [ ] `CHANGELOG.md` clearly states whether observer semantics changed.
- [ ] `mvn test` passes.
