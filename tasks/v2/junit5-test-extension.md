# JUnit 5 Test Extension

Roadmap item: `1.4 JUnit 5 @MockHttpServer extension`.

Suggested release: later 1.x, after consumer demand is clear.

## Scope

- Add `@MockHttpServer` annotation for `MockReactiveHttpClient<T>` fields.
- Add a JUnit 5 extension that wires a fresh mock proxy per test.
- Reset recorded exchanges between test methods.
- Decide whether the extension belongs in `reactive-http-client-test` or a separate `reactive-http-client-test-junit5` artifact.

## Acceptance

- [x] Annotation and extension classes are implemented.
- [x] At least one end-to-end consuming test demonstrates field injection and matcher registration.
- [x] Dependency scope does not force JUnit 5 onto non-JUnit consumers unless intentionally accepted.
- [x] README and `docs/14-test-helpers.md` include usage examples.
- [x] `mvn test` passes.
