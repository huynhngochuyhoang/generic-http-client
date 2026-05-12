# TLS Integration Tests

Roadmap item: `3.1 TLS integration test with a real self-signed peer`.

Suggested release: no version impact unless published with other changes.

## Scope

- Add in-process TLS integration coverage in `reactive-http-client-starter`.
- Use a local Reactor Netty server with a self-signed certificate.
- Exercise starter TLS configuration through an actual `WebClient` call.

## Acceptance

- [ ] Happy-path request succeeds against a trusted self-signed peer.
- [ ] Negative test fails with TLS handshake error when the peer certificate is not trusted.
- [ ] Tests are fully local and network-free.
- [ ] Tests complete in under 5 seconds on normal CI hardware.
- [ ] `mvn test` passes.
