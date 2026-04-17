# reactive-http-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huynhngochuyhoang/reactive-http-client-starter.svg)](https://search.maven.org/artifact/io.github.huynhngochuyhoang/reactive-http-client-starter)
[![CI](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml)

Spring Boot starter để tạo **declarative reactive HTTP client** (annotation-driven WebFlux client), có sẵn:
- mapping request/response
- timeout
- error decoding
- Resilience4j hooks (optional)
- Micrometer observability (optional)
- Correlation ID propagation

English summary: a Spring Boot starter for building annotation-driven reactive HTTP clients with optional resilience and observability integration.

---

## 1) Production readiness hiện tại

### Đánh giá nhanh

**Mức hiện tại: có thể chạy production ổn cho phần lớn service nội bộ nếu hoàn thiện thêm lớp vận hành/bảo mật ở ứng dụng; chưa full enterprise-ready out-of-the-box.**

| Nhóm | Trạng thái | Ghi chú |
|---|---|---|
| Core client proxy + annotation model | ✅ Tốt | Cơ chế proxy + metadata cache rõ ràng, đã có test |
| Timeout / error contract | ✅ Tốt | Có phân loại lỗi + timeout precedence |
| Resilience (CB/Retry/Bulkhead) | ✅ Tốt (opt-in) | Tích hợp được, retry mặc định GET/HEAD |
| Metrics/tracing hooks | ✅ Tốt (opt-in) | Có Micrometer observer + tag chuẩn |
| Security/auth chuẩn enterprise | ⚠️ Thiếu một phần | Chưa có built-in auth/token refresh/mTLS/proxy policy ở mức starter API |
| Operational hardening (governance) | ⚠️ Thiếu một phần | Chưa có readiness checklist/guardrails mạnh cho production |
| Integration/contract testing mẫu | ⚠️ Thiếu | Chủ yếu unit test trong starter |

### Những điểm còn thiếu

#### App cần tự hoàn thiện ngay khi dùng production

1. **Chuẩn hóa auth outbound**: cơ chế chung cho OAuth2/JWT/API key (inject header tự động thay vì service nào cũng tự làm)
2. **Network hardening policy**: chuẩn cấu hình proxy, SSL/mTLS, connection pool tuning theo môi trường
3. **PII-safe logging policy**: redaction/masking strategy khi bật logging body
4. **Production runbook**: hướng dẫn rõ “khi lỗi tăng / timeout tăng / circuit open thì xử lý gì”

#### Nên bổ sung vào roadmap starter

5. **Integration sample**: demo app + mock upstream để validate hành vi thực tế (retry, timeout, metrics)

> Lưu ý: Các mục trên không chặn việc dùng production, nhưng là phần cần hoàn thiện để vận hành lớn và an toàn hơn.

---

## 2) Quick Start (nhìn vào là chạy được)

### 2.1 Yêu cầu
- Java 17+
- Spring Boot 3.x
- Maven 3.8+

### 2.2 Thêm dependency starter

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-starter</artifactId>
  <version>1.1.0</version>
</dependency>
```

Trong app WebFlux, cần có `spring-boot-starter-webflux`.

### 2.3 Enable scanning

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 2.4 Khai báo client interface

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @ApiName("user.getById")
    @TimeoutMs(5000)
    Mono<UserDto> getUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand
    );

    @POST("/users")
    Mono<UserDto> createUser(
            @Body CreateUserRequest body,
            @HeaderParam("X-Tenant") String tenant
    );
}
```

### 2.5 Cấu hình `application.yml`

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.com
        connect-timeout-ms: 2000
        read-timeout-ms: 5000
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        log-body: false
        resilience:
          enabled: true
          circuit-breaker: user-service
          retry: user-service
          bulkhead: user-service
          timeout-ms: 0
```

### 2.6 Inject và dùng

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserApiClient userApiClient;

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }
}
```

---

## 3) Cách hoạt động cốt lõi

Mỗi call qua proxy đi theo pipeline:

1. Parse metadata từ annotation method.
2. Resolve tham số (`@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body`).
3. Gọi WebClient.
4. Decode lỗi:
   - 4xx -> `HttpClientException`
   - 5xx -> `RemoteServiceException`
5. Apply resilience (nếu bật): circuit-breaker -> retry -> bulkhead.
6. Apply timeout (ưu tiên `@TimeoutMs` > `read-timeout-ms` > `resilience.timeout-ms`).
7. Emit observability event (nếu có observer).

---

## 4) Annotation reference

| Annotation | Dùng ở | Ý nghĩa |
|---|---|---|
| `@ReactiveHttpClient(name, baseUrl)` | Interface | Khai báo HTTP client |
| `@GET/@POST/@PUT/@DELETE(path)` | Method | HTTP verb + path |
| `@PathVar(name)` | Parameter | Path variable |
| `@QueryParam(name)` | Parameter | Query param |
| `@HeaderParam(name)` | Parameter | Header |
| `@HeaderParam Map<String, String>` | Parameter | Inject nhiều header động |
| `@Body` | Parameter | Request body |
| `@ApiName("...")` | Method | Logical API name cho metrics/tracing |
| `@TimeoutMs(ms)` | Method | Override timeout theo method (`0` = tắt timeout method) |
| `@LogHttpExchange` | Method | Hook log request/response theo `HttpExchangeLogger` |

---

## 5) Error handling contract

| Trường hợp | Exception | Category |
|---|---|---|
| 429 | `HttpClientException` | `RATE_LIMITED` |
| 4xx (khác 429) | `HttpClientException` | `CLIENT_ERROR` |
| 5xx | `RemoteServiceException` | `SERVER_ERROR` |
| Timeout | `TimeoutException` | `—` (được chuẩn hóa thành `TIMEOUT` trong observability) |

Hai exception chính đều expose:
- `getStatusCode()`
- `getResponseBody()`
- `getErrorCategory()`

---

## 6) Resilience4j integration

Starter hỗ trợ integration theo client-level config. Retry mặc định chỉ áp dụng cho **GET/HEAD** (an toàn idempotency).

Nếu cần policy theo từng business method hoặc fallback method, dùng thêm annotation Resilience4j ở service layer.

### Dependency thường dùng ở consumer app

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-reactor</artifactId>
</dependency>
```

---

## 7) Observability (Micrometer)

Khi có `MeterRegistry`, starter tự ghi metric timer (mặc định: `http.client.requests`) với các tag chính:
- `client.name`
- `api.name`
- `http.method`
- `http.status_code`
- `outcome`
- `exception`
- `error.category`
- `uri` (có thể tắt qua `include-url-path`)

### Cấu hình observability

```yaml
reactive:
  http:
    observability:
      enabled: true
      metric-name: http.client.requests
      include-url-path: true
      log-request-body: false
      log-response-body: false
```

> Khuyến nghị production: chỉ bật body logging khi thật cần thiết và phải có masking PII.

---

## 8) Checklist để dùng production an toàn

- [ ] Mọi client đều set `base-url`, timeout và resilience rõ ràng theo SLA.
- [ ] Có policy retry hợp lệ (không retry write bừa bãi).
- [ ] Có dashboard + alert (latency, error rate, circuit-open, timeout).
- [ ] Có correlation-id end-to-end.
- [ ] Có cơ chế auth outbound chuẩn hóa (token rotation/refresh).
- [ ] Có quy định không log PII/secret.
- [ ] Có integration test cho các case: timeout, 4xx/5xx, retry, fallback.
- [ ] Có runbook vận hành khi upstream suy giảm.

---

## 9) Build & test

```bash
mvn -B -ntp verify
```

---

## 10) Module structure

```text
reactive-http-client/
├── pom.xml
├── CHANGELOG.md
└── reactive-http-client-starter/
    ├── pom.xml
    └── src/main/java/io/github/huynhngochuyhoang/httpstarter/
        ├── annotation/
        ├── config/
        ├── core/
        ├── enable/
        ├── exception/
        ├── filter/
        └── observability/
```

---

## 11) License

Apache License 2.0.
