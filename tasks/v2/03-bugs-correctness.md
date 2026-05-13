# V2 Bugs / Correctness To Fix

Roadmap bucket: `3. Bugs / correctness to fix`.

## Tasks

- [ ] **3.1 TLS integration test with a real self-signed peer**
  - Add local HTTPS server.
  - Cover trusted and untrusted certificate paths.
  - Keep tests network-free and fast.
- [ ] **3.2 Remove deprecated `log-body` property**
  - Hold for `2.0.0`.
  - Remove property, metadata, and compatibility branch.
  - Add migration note.
- [x] **3.3 Verify OTel propagation disable semantics**
  - Decide whether span and propagation toggles should be separate.
  - Test disabled observer, disabled propagation, and default behavior.
  - Document exactly what each switch controls.
- [ ] **3.4 Observer constructor compatibility audit**
  - Preserve or deprecate constructors intentionally before adding event fields.
  - Add compatibility tests for custom observers.

## Release Notes

- 3.1 and 3.3 can ship as patch/minor work depending on implementation.
- 3.2 is intentionally held for `2.0.0`.
- 3.4 should be completed before any observer-event shape change.
