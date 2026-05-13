# Auth Providers

Roadmap items: `1.1 AWS SigV4 auth provider`, `1.2 Property-driven auth-provider configuration`.

Suggested release: `1.15.0+`.

## Scope

- Add `AwsSigV4AuthProvider implements AuthProvider` in the starter `auth` package.
- Add an `AuthProviderFactory` SPI for property-driven provider construction.
- Support both existing string bean references and new object-style auth config.
- Ship built-in factories for `oauth2-client-credentials` and `aws-sigv4`.

## Acceptance

- [x] SigV4 canonical request, string-to-sign, and final `Authorization` header match AWS reference vectors.
- [x] Body signing reuses cached raw body bytes captured for auth filters.
- [x] Builder API has validation and Javadoc.
- [x] Property binding tests cover string-form compatibility and object-form provider creation.
- [x] README and docs show OAuth2 and AWS SigV4 YAML examples.
- [x] `mvn test` passes.

## Out Of Scope

- SigV4a.
- STS assume-role flow.
- Pre-signed URLs.
