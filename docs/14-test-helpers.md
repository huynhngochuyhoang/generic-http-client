# Test Helpers (`reactive-http-client-test`)

The starter ships a companion artifact for unit-testing `@ReactiveHttpClient` interfaces without standing up a real HTTP server.

---

## Add the dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-test</artifactId>
  <version>${reactive-http-client.version}</version>
  <scope>test</scope>
</dependency>
```

---

## `MockReactiveHttpClient`

`MockReactiveHttpClient` builds a real proxy backed by an in-process `ExchangeFunction`, records every outbound exchange, and serves canned responses based on registered matchers.

### Basic setup

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .baseUrl("http://mock.local")
        .respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"))
        .respondTo(HttpMethod.POST, "/users",
                ex -> MockReactiveHttpClient.json(201, "{\"id\":7}"))
        .build();
```

### Invoking and asserting

```java
User user = mock.proxy().getUser(42).block();
assertThat(user.getName()).isEqualTo("alice");

RecordedExchange recorded = mock.lastExchange();
assertThat(recorded.method()).isEqualTo(HttpMethod.GET);
assertThat(recorded.uri().getPath()).isEqualTo("/users/42");
```

### Unmatched requests

Requests that do not match any registered matcher fall through to a configurable fallback response (HTTP 404 by default), so tests fail loudly instead of hanging on a missing matcher.

---

## `RecordedExchange`

Every call through the mock proxy is recorded. `RecordedExchange` exposes:

| Method | Returns | Description |
|---|---|---|
| `method()` | `HttpMethod` | HTTP verb of the outbound request |
| `uri()` | `URI` | Full request URI including path and query |
| `headers()` | `HttpHeaders` | Request headers |
| `body()` | `byte[]` | Serialized request body bytes |

```java
RecordedExchange exchange = mock.lastExchange();
assertThat(exchange.method()).isEqualTo(HttpMethod.POST);
assertThat(exchange.uri().getPath()).isEqualTo("/users");
assertThat(exchange.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
```

---

## `ErrorCategoryAssertions`

A fluent helper for asserting on the starter's error contract:

```java
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
        .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
        .hasStatusCode(404);
```

Available assertion methods:

| Method | Description |
|---|---|
| `hasErrorCategory(ErrorCategory)` | Asserts the `ErrorCategory` of the thrown exception |
| `hasStatusCode(int)` | Asserts the HTTP status code |

---

## Simulating error responses

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .baseUrl("http://mock.local")
        .respondTo(HttpMethod.GET, "/users/99",
                ex -> MockReactiveHttpClient.json(404, "{\"error\":\"not found\"}"))
        .respondTo(HttpMethod.GET, "/users/1",
                ex -> MockReactiveHttpClient.json(500, "{\"error\":\"internal error\"}"))
        .build();

// Assert 404 -> CLIENT_ERROR
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
        .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
        .hasStatusCode(404);

// Assert 500 -> SERVER_ERROR
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(1))
        .hasErrorCategory(ErrorCategory.SERVER_ERROR)
        .hasStatusCode(500);
```

---

## Using with Spring Boot test slices

```java
@SpringBootTest
class UserServiceTest {

    MockReactiveHttpClient<UserApiClient> mockClient = MockReactiveHttpClient
            .forClient(UserApiClient.class)
            .baseUrl("http://mock.local")
            .respondTo(HttpMethod.GET, "/users/42",
                    ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"))
            .build();

    @MockBean
    UserApiClient userApiClient;

    @BeforeEach
    void setUp() {
        // inject the mock proxy
        ReflectionTestUtils.setField(userApiClient, "delegate", mockClient.proxy());
    }

    @Test
    void getsUser() {
        User user = mockClient.proxy().getUser(42).block();
        assertThat(user.getId()).isEqualTo(42);
    }
}
```
