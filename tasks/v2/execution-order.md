# V2 Execution Order

Implementation order for `ROADMAP_V2.md`, based on its suggested execution order.

Current baseline: `v1.14.0` (2026-05-12).

| Status | Order | Roadmap items | Task files | Why now | Verify |
|---|---|---|---|---|---|
| Done | 1 | `3.3` + `2.3` | [Bugs / correctness](03-bugs-correctness.md), [Features to optimize](02-features-to-optimize.md) | Lock down OTel propagation semantics and filter diagnostics while the v1.14.0 propagation work is fresh. | Propagation-disable and diagnostics tests pass. |
| Done | 2 | `1.1` + `1.2` | [Auth providers](auth-providers.md) | AWS SigV4 plus property-driven auth providers are the highest user-visible missing capability. | Auth-provider acceptance checks pass and docs include OAuth2/AWS SigV4 examples. |
| Done | 3 | `2.1` + `1.3` + `3.4` | [Observability deepening](observability-deepening.md) | Observer event expansion and composite observer work should be designed together. | Observer compatibility and OTel attribute tests pass. |
| Done | 4 | `3.1` | [TLS integration tests](tls-integration-tests.md) | Pure hardening with low product risk. | TLS integration test uses a real self-signed peer and passes. |
| Done | 5 | `1.5` | [Features to add](01-features-to-add.md) | Rate-limiter support is useful, but should follow observer/auth work unless a user need appears first. | Rate-limiter behavior and configuration tests pass. |
| Done | 6 | `1.4` + `2.4` | [JUnit 5 test extension](junit5-test-extension.md), [Features to optimize](02-features-to-optimize.md) | Documentation examples and the JUnit 5 extension are ready for this release. | Examples are documented; extension tests pass. |
| Done | 7 | `3.2` | [2.0 cleanup](2.0-cleanup.md) | Completed for the `2.0.0` breaking-change release. | Deprecated `log-body` compatibility is removed with migration docs. |

Run at least `mvn test` before release-candidate commits.
