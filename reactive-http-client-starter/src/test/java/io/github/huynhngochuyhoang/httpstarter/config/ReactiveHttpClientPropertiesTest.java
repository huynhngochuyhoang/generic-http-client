package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReactiveHttpClientPropertiesTest {

    @Test
    void shouldUseExpectedClientDefaults() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertFalse(config.getResilience().isEnabled());
        assertEquals(0, config.getResilience().getTimeoutMs());
        assertTrue(config.getResilience().getRetryMethods().contains("GET"));
        assertTrue(config.getResilience().getRetryMethods().contains("HEAD"));
        assertEquals(2, config.getCodecMaxInMemorySizeMb());
        assertFalse(config.isCompressionEnabled());
        assertFalse(config.isHttp2Enabled());
        assertFalse(config.isLogExchange());
        assertFalse(config.isExchangeLoggingEnabled());
        assertEquals(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY, config.getLogPreset());
        assertNull(config.getAuthProvider());
        assertNull(config.getAuth());
        assertNotNull(config.getDefaultHeaders());
        assertTrue(config.getDefaultHeaders().isEmpty());
        assertNotNull(config.getDefaultQueryParams());
        assertTrue(config.getDefaultQueryParams().isEmpty());
        assertNotNull(config.getApis());
        assertTrue(config.getApis().isEmpty());
    }

    @Test
    void shouldBindLegacyAuthProviderBeanName() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.users.auth-provider", "userServiceAuthProvider");

        ReactiveHttpClientProperties bound = bind(yaml);

        ReactiveHttpClientProperties.ClientConfig config = bound.getClients().get("users");
        assertEquals("userServiceAuthProvider", config.getAuthProvider());
        assertNull(config.getAuth());
        assertTrue(config.hasAuthConfigured());
    }

    @Test
    void shouldBindObjectStyleAwsSigV4AuthConfig() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.payments.auth.type", "aws-sigv4");
        yaml.put("reactive.http.clients.payments.auth.aws-sig-v4.access-key-id", "key");
        yaml.put("reactive.http.clients.payments.auth.aws-sig-v4.secret-access-key", "secret");
        yaml.put("reactive.http.clients.payments.auth.aws-sig-v4.region", "us-east-1");
        yaml.put("reactive.http.clients.payments.auth.aws-sig-v4.service", "execute-api");

        ReactiveHttpClientProperties bound = bind(yaml);

        ReactiveHttpClientProperties.ClientConfig config = bound.getClients().get("payments");
        assertNull(config.getAuthProvider());
        assertTrue(config.hasAuthConfigured());
        assertEquals("aws-sigv4", config.getAuth().getType());
        assertEquals("key", config.getAuth().getAwsSigV4().getAccessKeyId());
        assertEquals("execute-api", config.getAuth().getAwsSigV4().getService());
    }

    @Test
    void shouldUseLogExchangeForExchangeLogging() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertFalse(config.isExchangeLoggingEnabled());
        config.setLogExchange(true);
        assertTrue(config.isExchangeLoggingEnabled());
    }

    @Test
    void shouldBindExchangeLoggingPreset() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.audit.log-exchange", true);
        yaml.put("reactive.http.clients.audit.log-preset", "headers");

        ReactiveHttpClientProperties bound = bind(yaml);

        ReactiveHttpClientProperties.ClientConfig config = bound.getClients().get("audit");
        assertTrue(config.isExchangeLoggingEnabled());
        assertEquals(ReactiveHttpClientProperties.LogPreset.HEADERS, config.getLogPreset());
    }

    @Test
    void shouldBindPerClientHttp2OptIn() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.inventory.http2-enabled", true);
        yaml.put("reactive.http.clients.users.base-url", "https://users.example");

        ReactiveHttpClientProperties bound = bind(yaml);

        assertTrue(bound.getClients().get("inventory").isHttp2Enabled());
        assertFalse(bound.getClients().get("users").isHttp2Enabled());
    }

    @Test
    void shouldBindPerClientDefaultHeaders() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.inventory.default-headers.X-Tenant", "public");
        yaml.put("reactive.http.clients.inventory.default-headers.X-Client-Version", "v1");

        ReactiveHttpClientProperties bound = bind(yaml);

        Map<String, String> defaultHeaders = bound.getClients().get("inventory").getDefaultHeaders();
        assertEquals("public", defaultHeaders.get("X-Tenant"));
        assertEquals("v1", defaultHeaders.get("X-Client-Version"));
    }

    @Test
    void shouldBindPerClientDefaultQueryParams() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.inventory.default-query-params.locale[0]", "en-US");
        yaml.put("reactive.http.clients.inventory.default-query-params.tag[0]", "public");
        yaml.put("reactive.http.clients.inventory.default-query-params.tag[1]", "stable");

        ReactiveHttpClientProperties bound = bind(yaml);

        Map<String, java.util.List<String>> defaultQueryParams =
                bound.getClients().get("inventory").getDefaultQueryParams();
        assertEquals(java.util.List.of("en-US"), defaultQueryParams.get("locale"));
        assertEquals(java.util.List.of("public", "stable"), defaultQueryParams.get("tag"));
    }

    @Test
    void shouldNormalizeRetryMethodsToUpperCase() {
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setRetryMethods(java.util.Set.of("get", " Put ", "HEAD"));

        assertTrue(resilienceConfig.getRetryMethods().contains("GET"));
        assertTrue(resilienceConfig.getRetryMethods().contains("PUT"));
        assertTrue(resilienceConfig.getRetryMethods().contains("HEAD"));
    }

    @Test
    void shouldUseExpectedGlobalNetworkDefaults() {
        ReactiveHttpClientProperties.NetworkConfig network = new ReactiveHttpClientProperties.NetworkConfig();

        assertEquals(2000, network.getConnectTimeoutMs());
        assertEquals(60_000, network.getNetworkReadTimeoutMs());
        assertEquals(60_000, network.getNetworkWriteTimeoutMs());
        assertEquals(60_000, network.getReadTimeoutMs(),
                "legacy getReadTimeoutMs must delegate to the new field");
        assertEquals(60_000, network.getWriteTimeoutMs(),
                "legacy getWriteTimeoutMs must delegate to the new field");
        assertEquals(200, network.getConnectionPool().getMaxConnections());
        assertEquals(5000, network.getConnectionPool().getPendingAcquireTimeoutMs());
        assertEquals(0, network.getConnectionPool().getMaxIdleTimeMs());
        assertEquals(0, network.getConnectionPool().getMaxLifeTimeMs());
        assertEquals(0, network.getConnectionPool().getEvictInBackgroundMs());
    }

    @Test
    void legacyTimeoutPropertiesStillBind() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.network.read-timeout-ms", 15_000);
        yaml.put("reactive.http.network.write-timeout-ms", 25_000);

        ReactiveHttpClientProperties bound = bind(yaml);

        assertEquals(15_000, bound.getNetwork().getNetworkReadTimeoutMs());
        assertEquals(25_000, bound.getNetwork().getNetworkWriteTimeoutMs());
        assertEquals(15_000, bound.getNetwork().getReadTimeoutMs());
        assertEquals(25_000, bound.getNetwork().getWriteTimeoutMs());
    }

    @Test
    void canonicalTimeoutPropertiesBind() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.network.network-read-timeout-ms", 11_000);
        yaml.put("reactive.http.network.network-write-timeout-ms", 22_000);

        ReactiveHttpClientProperties bound = bind(yaml);

        assertEquals(11_000, bound.getNetwork().getNetworkReadTimeoutMs());
        assertEquals(22_000, bound.getNetwork().getNetworkWriteTimeoutMs());
    }

    @Test
    void poolMetricsFlagDefaultsOffAndBindsFromYaml() {
        ReactiveHttpClientProperties.ConnectionPoolConfig defaults =
                new ReactiveHttpClientProperties.ConnectionPoolConfig();
        assertFalse(defaults.isMetricsEnabled(),
                "pool metrics must be off by default to avoid per-request overhead");

        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.network.connection-pool.metrics-enabled", true);
        ReactiveHttpClientProperties bound = bind(yaml);

        assertTrue(bound.getNetwork().getConnectionPool().isMetricsEnabled());
    }

    @Test
    void perClientPoolOverrideIsNullByDefaultSoItInheritsGlobal() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertNull(config.getPool(),
                "client pool must default to null so the factory bean falls back to the global pool");
    }

    @Test
    void perClientPoolOverrideBindsEveryField() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.network.connection-pool.max-connections", 50);
        yaml.put("reactive.http.clients.big.pool.max-connections", 500);
        yaml.put("reactive.http.clients.big.pool.pending-acquire-timeout-ms", 7_500);
        yaml.put("reactive.http.clients.big.pool.max-idle-time-ms", 30_000);
        yaml.put("reactive.http.clients.big.pool.max-life-time-ms", 300_000);
        yaml.put("reactive.http.clients.big.pool.evict-in-background-ms", 60_000);
        yaml.put("reactive.http.clients.small.base-url", "https://small.example");

        ReactiveHttpClientProperties bound = bind(yaml);

        assertEquals(50, bound.getNetwork().getConnectionPool().getMaxConnections());

        ReactiveHttpClientProperties.ConnectionPoolConfig bigPool = bound.getClients().get("big").getPool();
        assertNotNull(bigPool, "client-level pool override must bind when YAML supplies it");
        assertEquals(500, bigPool.getMaxConnections());
        assertEquals(7_500, bigPool.getPendingAcquireTimeoutMs());
        assertEquals(30_000, bigPool.getMaxIdleTimeMs());
        assertEquals(300_000, bigPool.getMaxLifeTimeMs());
        assertEquals(60_000, bigPool.getEvictInBackgroundMs());

        assertNull(bound.getClients().get("small").getPool(),
                "clients without an explicit pool block must keep pool == null so they inherit the global one");
    }

    @Test
    void inheritedPoolStaysReferenceEqualToGlobal() {
        ReactiveHttpClientProperties.NetworkConfig network = new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.ClientConfig client = new ReactiveHttpClientProperties.ClientConfig();

        ReactiveHttpClientProperties.ConnectionPoolConfig effective =
                client.getPool() != null ? client.getPool() : network.getConnectionPool();

        assertSame(network.getConnectionPool(), effective,
                "client with no override must see the exact global pool config instance");
    }

    @Test
    void apiMapBindsMethodPathAndTimeout() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("reactive.http.clients.user-service.apis.get-user.method", "get");
        yaml.put("reactive.http.clients.user-service.apis.get-user.path", "/users/{id}");
        yaml.put("reactive.http.clients.user-service.apis.get-user.timeout-ms", 1200);

        ReactiveHttpClientProperties bound = bind(yaml);
        ReactiveHttpClientProperties.ApiConfig apiConfig =
                bound.getClients().get("user-service").getApis().get("get-user");

        assertNotNull(apiConfig);
        assertEquals("GET", apiConfig.getMethod());
        assertEquals("/users/{id}", apiConfig.getPath());
        assertEquals(1200, apiConfig.getTimeoutMs());
    }

    @Test
    void apiMapWithDotKeyBindsViaBracketNotation() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reactive.http.clients.user-service.apis[user.getById].method", "get");
        properties.put("reactive.http.clients.user-service.apis[user.getById].path", "/users/{id}");
        properties.put("reactive.http.clients.user-service.apis[user.getById].timeout-ms", 1200);

        ReactiveHttpClientProperties bound = bind(properties);
        ReactiveHttpClientProperties.ApiConfig apiConfig =
                bound.getClients().get("user-service").getApis().get("user.getById");

        assertNotNull(apiConfig);
        assertEquals("GET", apiConfig.getMethod());
        assertEquals("/users/{id}", apiConfig.getPath());
        assertEquals(1200, apiConfig.getTimeoutMs());
    }

    @Test
    void apiMapTimeoutRejectsValuesAboveThirtyMinutes() {
        ReactiveHttpClientProperties.ApiConfig apiConfig = new ReactiveHttpClientProperties.ApiConfig();
        assertThrows(IllegalArgumentException.class, () -> apiConfig.setTimeoutMs(30L * 60 * 1000 + 1));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ReactiveHttpClientProperties bind(Map<String, Object> yaml) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(yaml);
        return new Binder(source)
                .bindOrCreate("reactive.http", Bindable.of(ReactiveHttpClientProperties.class));
    }
}
