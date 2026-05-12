# V2 Task Index

Actionable task breakdown for `ROADMAP_V2.md`.

Current baseline: `v1.14.0` (2026-05-12).

| Task | Roadmap item | Suggested release | Notes |
|---|---|---|---|
| [Features to add](01-features-to-add.md) | 1.x | `1.15.0+` | Net-new capability backlog |
| [Features to optimize](02-features-to-optimize.md) | 2.x | `1.15.0+` | Performance, clarity, and ergonomics backlog |
| [Bugs / correctness](03-bugs-correctness.md) | 3.x | patch/minor/major by risk | Correctness and compatibility backlog |
| [Auth providers](auth-providers.md) | 1.1, 1.2 | `1.15.0+` | AWS SigV4 plus property-driven auth-provider wiring |
| [JUnit 5 test extension](junit5-test-extension.md) | 1.4 | later 1.x | Convenience API; wait for consumer demand if needed |
| [Observability deepening](observability-deepening.md) | 1.3, 2.1, 3.4 | `1.15.0+` or `2.0.0` | Composite observer plus `server.address` |
| [TLS integration tests](tls-integration-tests.md) | 3.1 | no version impact | Test-only hardening |
| [2.0 cleanup](2.0-cleanup.md) | 3.2 | `2.0.0` | Remove deprecated `log-body` compatibility |

## Working Rules

- Keep each task PR scoped to the listed files and acceptance checks unless a dependency forces a small supporting change.
- Update `CHANGELOG.md`, README, and the relevant `docs/*.md` file in the same PR as user-visible behavior.
- Run at least `mvn test` before release-candidate commits.
- If a task changes public override semantics, call it out explicitly in `CHANGELOG.md`.
