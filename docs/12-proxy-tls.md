# HTTP Proxy and TLS / mTLS

Both outbound proxy routing and custom TLS/mTLS are configured under the `reactive.http.network` block (global) and can be overridden per client under `reactive.http.clients.<name>`. When a per-client block is present it replaces the global block wholesale — there is no field-level merging.

---

## HTTP proxy

### Global proxy

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP                             # HTTP | HTTPS | SOCKS4 | SOCKS5 | NONE
        host: proxy.corp.example
        port: 3128
        username: ${PROXY_USER}               # optional
        password: ${PROXY_PASS}               # optional
        non-proxy-hosts: "localhost|.*\\.internal"   # Java regex, not glob
```

### Per-client proxy override

```yaml
reactive:
  http:
    clients:
      partner-api:
        proxy:
          type: HTTP
          host: partner-proxy.example.com
          port: 8080
```

### Bypassing the global proxy for one client

Set `type: NONE` on the per-client proxy block to route that client directly, bypassing any inherited global proxy:

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example
        port: 3128
    clients:
      internal-service:
        proxy:
          type: NONE   # bypass global proxy; connect directly
```

### `non-proxy-hosts` pattern

`non-proxy-hosts` is a Java `java.util.regex.Pattern`, not a glob. Pipe (`|`) separates alternatives:

```yaml
non-proxy-hosts: "localhost|127\\.0\\.0\\.1|.*\\.internal|.*\\.corp\\.example"
```

Use `.*\.internal` (regex) — **not** `*.internal` (glob).

---

## TLS / mTLS

### Custom truststore

Use a custom truststore when the upstream service presents a certificate signed by a private CA:

```yaml
reactive:
  http:
    network:
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        trust-store-type: PKCS12        # default
```

### Client certificate (mTLS)

Add `key-store` to present a client certificate to the upstream server:

```yaml
reactive:
  http:
    network:
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12
        key-store-password: changeit
        key-store-type: PKCS12          # default
```

### Protocol and cipher restrictions

```yaml
reactive:
  http:
    network:
      tls:
        protocols: [TLSv1.3, TLSv1.2]
        ciphers:
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
```

### Per-client TLS override

Each client can use a different truststore or client certificate:

```yaml
reactive:
  http:
    clients:
      partner-api:
        tls:
          trust-store: classpath:certs/partner-ts.p12
          trust-store-password: ${PARTNER_TS_PWD}
          key-store: classpath:certs/partner-client.p12
          key-store-password: ${PARTNER_CLIENT_PWD}
```

### Resource path resolution

Truststore and keystore paths are resolved via Spring's `DefaultResourceLoader`:

| Prefix | Example |
|---|---|
| `classpath:` | `classpath:certs/truststore.p12` |
| `file:` | `file:/etc/ssl/truststore.p12` |
| Absolute path | `/etc/ssl/truststore.p12` |

### Development: disable certificate validation

```yaml
reactive:
  http:
    network:
      tls:
        insecure-trust-all: true   # development only
```

The starter logs a **WARN** at startup when `insecure-trust-all: true` is set so it can never be enabled accidentally. Never use this in production.

---

## Full example

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example
        port: 3128
        username: ${PROXY_USER}
        password: ${PROXY_PASS}
        non-proxy-hosts: "localhost|.*\\.internal"
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12
        key-store-password: changeit
        protocols: [TLSv1.3, TLSv1.2]
    clients:
      internal-service:
        base-url: https://internal.corp.example
        proxy:
          type: NONE          # bypass global proxy
      partner-api:
        base-url: https://partner.example.com
        tls:
          trust-store: classpath:certs/partner-ts.p12
          trust-store-password: ${PARTNER_TS_PWD}
```
