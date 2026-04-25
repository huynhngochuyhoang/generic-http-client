package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(config.isLogExchange());
        assertFalse(config.isExchangeLoggingEnabled());
        assertFalse(config.isLogBody());
        assertNull(config.getAuthProvider());
    }

    @Test
    void shouldTreatLegacyLogBodyAsAliasForExchangeLogging() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        config.setLogBody(true);
        assertTrue(config.isExchangeLoggingEnabled());

        config.setLogBody(false);
        config.setLogExchange(true);
        assertTrue(config.isExchangeLoggingEnabled());
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ReactiveHttpClientProperties bind(Map<String, Object> yaml) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(yaml);
        return new Binder(source)
                .bindOrCreate("reactive.http", Bindable.of(ReactiveHttpClientProperties.class));
    }
}
