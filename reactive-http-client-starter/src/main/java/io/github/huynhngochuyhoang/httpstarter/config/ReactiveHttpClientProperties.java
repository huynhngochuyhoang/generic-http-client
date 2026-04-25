package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.SensitiveHeaders;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration properties for all reactive HTTP clients.
 * <p>
 * Example {@code application.yml}:
 * <pre>{@code
 * reactive:
 *   http:
 *     network:
 *       connect-timeout-ms: 2000
 *       read-timeout-ms: 5000
 *       write-timeout-ms: 5000
 *       connection-pool:
 *         max-connections: 200
 *         pending-acquire-timeout-ms: 5000
 *     clients:
 *       user-service:
 *         base-url: https://api.example.com
 *         codec-max-in-memory-size-mb: 2
 *         compression-enabled: false
 *         log-exchange: false
 *         auth-provider: userServiceAuthProvider
 *         resilience:
 *           enabled: false
 *           circuit-breaker: default
 *           retry: default
 *           retry-methods: [GET, HEAD]
 *           bulkhead: default
 *           timeout-ms: 0
 * }</pre>
 */
@ConfigurationProperties(prefix = "reactive.http")
public class ReactiveHttpClientProperties {

    private NetworkConfig network = new NetworkConfig();
    private Map<String, ClientConfig> clients = new HashMap<>();
    private CorrelationIdConfig correlationId = new CorrelationIdConfig();
    private InboundHeadersConfig inboundHeaders = new InboundHeadersConfig();

    public NetworkConfig getNetwork() { return network; }
    public void setNetwork(NetworkConfig network) { this.network = network; }

    public Map<String, ClientConfig> getClients() { return clients; }
    public void setClients(Map<String, ClientConfig> clients) { this.clients = clients; }

    public CorrelationIdConfig getCorrelationId() { return correlationId; }
    public void setCorrelationId(CorrelationIdConfig correlationId) {
        this.correlationId = correlationId != null ? correlationId : new CorrelationIdConfig();
    }

    public InboundHeadersConfig getInboundHeaders() { return inboundHeaders; }
    public void setInboundHeaders(InboundHeadersConfig inboundHeaders) {
        this.inboundHeaders = inboundHeaders != null ? inboundHeaders : new InboundHeadersConfig();
    }

    // ---- global network configuration ----

    /**
     * Global Netty-level network policy applied to every client.
     *
     * <p>Two distinct timeout layers act on outbound calls, and confusion between
     * them has been a repeat source of incidents:
     *
     * <ul>
     *   <li><b>Network safety-net timeouts</b>
     *       ({@link #getNetworkReadTimeoutMs() network-read-timeout-ms} /
     *        {@link #getNetworkWriteTimeoutMs() network-write-timeout-ms}) —
     *       Netty {@code ReadTimeoutHandler} / {@code WriteTimeoutHandler}
     *       attached to every pooled connection. These are absolute upper bounds,
     *       sized larger than any per-request timeout. They catch stuck pooled
     *       connections, not ordinary slow responses.</li>
     *   <li><b>Per-request response timeouts</b>
     *       (method-level {@link io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs @TimeoutMs}
     *       or client-level {@code resilience.timeout-ms}) — applied via
     *       {@code HttpClientRequest.responseTimeout()} on each attempt. This is
     *       the timeout most callers want to tune.</li>
     * </ul>
     *
     * <p>The per-request timeout always fires first if both are set. The safety
     * nets should be set well above the largest business timeout. Defaults:
     * 60 s for each safety-net timeout; no per-request timeout.
     *
     * <p>The legacy property names {@code read-timeout-ms} and
     * {@code write-timeout-ms} are accepted as aliases for the canonical
     * {@code network-read-timeout-ms} / {@code network-write-timeout-ms}. Both
     * bind to the same backing field; pick one. The legacy names are deprecated
     * and will be removed in a future major release.
     */
    public static class NetworkConfig {
        private int connectTimeoutMs = 2000;
        private int networkReadTimeoutMs = 60_000;
        private int networkWriteTimeoutMs = 60_000;
        private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        /**
         * Canonical name for the Netty {@code ReadTimeoutHandler} safety-net timeout.
         * Fires when a pooled connection produces no inbound bytes for this duration.
         */
        public int getNetworkReadTimeoutMs() { return networkReadTimeoutMs; }
        public void setNetworkReadTimeoutMs(int networkReadTimeoutMs) { this.networkReadTimeoutMs = networkReadTimeoutMs; }

        /**
         * Canonical name for the Netty {@code WriteTimeoutHandler} safety-net timeout.
         * Fires when a pooled connection accepts no outbound bytes for this duration.
         */
        public int getNetworkWriteTimeoutMs() { return networkWriteTimeoutMs; }
        public void setNetworkWriteTimeoutMs(int networkWriteTimeoutMs) { this.networkWriteTimeoutMs = networkWriteTimeoutMs; }

        /**
         * @deprecated use {@link #getNetworkReadTimeoutMs()} / {@code network-read-timeout-ms}.
         *             Kept as a YAML alias bound to the same backing field.
         */
        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "reactive.http.network.network-read-timeout-ms")
        public int getReadTimeoutMs() { return networkReadTimeoutMs; }

        /** @deprecated setter retained so {@code read-timeout-ms} continues to bind. */
        @Deprecated
        public void setReadTimeoutMs(int readTimeoutMs) { this.networkReadTimeoutMs = readTimeoutMs; }

        /**
         * @deprecated use {@link #getNetworkWriteTimeoutMs()} / {@code network-write-timeout-ms}.
         *             Kept as a YAML alias bound to the same backing field.
         */
        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "reactive.http.network.network-write-timeout-ms")
        public int getWriteTimeoutMs() { return networkWriteTimeoutMs; }

        /** @deprecated setter retained so {@code write-timeout-ms} continues to bind. */
        @Deprecated
        public void setWriteTimeoutMs(int writeTimeoutMs) { this.networkWriteTimeoutMs = writeTimeoutMs; }

        public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
        public void setConnectionPool(ConnectionPoolConfig connectionPool) { this.connectionPool = connectionPool; }
    }

    public static class ConnectionPoolConfig {
        private int maxConnections = 200;
        private long pendingAcquireTimeoutMs = 5000;
        /** Idle duration after which a pooled connection is evicted. {@code 0} leaves Reactor Netty's default (no idle eviction). */
        private long maxIdleTimeMs = 0;
        /** Max lifetime of a pooled connection. {@code 0} leaves Reactor Netty's default (unlimited). */
        private long maxLifeTimeMs = 0;
        /** Interval at which the provider sweeps for evictable connections. {@code 0} disables background eviction. */
        private long evictInBackgroundMs = 0;
        /**
         * When {@code true}, the provider publishes Reactor Netty's built-in pool metrics
         * ({@code reactor.netty.connection.provider.*} gauges) to the globally-registered
         * {@code MeterRegistry}. Requires {@code micrometer-core} on the classpath;
         * leave {@code false} (the default) to avoid the small per-request overhead
         * when pool visibility isn't needed.
         */
        private boolean metricsEnabled = false;

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

        public long getPendingAcquireTimeoutMs() { return pendingAcquireTimeoutMs; }
        public void setPendingAcquireTimeoutMs(long pendingAcquireTimeoutMs) {
            this.pendingAcquireTimeoutMs = pendingAcquireTimeoutMs;
        }

        public long getMaxIdleTimeMs() { return maxIdleTimeMs; }
        public void setMaxIdleTimeMs(long maxIdleTimeMs) { this.maxIdleTimeMs = maxIdleTimeMs; }

        public long getMaxLifeTimeMs() { return maxLifeTimeMs; }
        public void setMaxLifeTimeMs(long maxLifeTimeMs) { this.maxLifeTimeMs = maxLifeTimeMs; }

        public long getEvictInBackgroundMs() { return evictInBackgroundMs; }
        public void setEvictInBackgroundMs(long evictInBackgroundMs) { this.evictInBackgroundMs = evictInBackgroundMs; }

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    }

    // ---- per-client configuration ----

    public static class ClientConfig {

        private String baseUrl;
        private int codecMaxInMemorySizeMb = 2;
        private boolean compressionEnabled = false;
        private boolean logExchange = false;
        @Deprecated
        private Boolean logBody;
        /**
         * Bean name of {@code AuthProvider} to use for this client.
         * Empty means no automatic auth injection.
         */
        private String authProvider;
        private ResilienceConfig resilience = new ResilienceConfig();
        /**
         * Per-client connection-pool override. When {@code null}, the client inherits
         * {@link NetworkConfig#getConnectionPool()}. When set, every field on this
         * instance takes precedence — there is no field-level merging.
         */
        private ConnectionPoolConfig pool;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getCodecMaxInMemorySizeMb() { return codecMaxInMemorySizeMb; }
        public void setCodecMaxInMemorySizeMb(int codecMaxInMemorySizeMb) { this.codecMaxInMemorySizeMb = codecMaxInMemorySizeMb; }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }

        public boolean isLogExchange() { return logExchange; }
        public void setLogExchange(boolean logExchange) { this.logExchange = logExchange; }

        /**
         * @deprecated use {@code log-exchange} / {@link #isLogExchange()} instead.
         */
        @Deprecated
        public boolean isLogBody() { return Boolean.TRUE.equals(logBody); }

        /**
         * @deprecated use {@code log-exchange} / {@link #setLogExchange(boolean)} instead.
         */
        @Deprecated
        public void setLogBody(boolean logBody) { this.logBody = logBody; }

        public boolean isExchangeLoggingEnabled() {
            return logExchange || Boolean.TRUE.equals(logBody);
        }

        public String getAuthProvider() { return authProvider; }
        public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

        public ResilienceConfig getResilience() { return resilience; }
        public void setResilience(ResilienceConfig resilience) { this.resilience = resilience; }

        public ConnectionPoolConfig getPool() { return pool; }
        public void setPool(ConnectionPoolConfig pool) { this.pool = pool; }
    }

    // ---- resilience sub-config ----

    public static class ResilienceConfig {

        private boolean enabled = false;
        /** Name of the Resilience4j CircuitBreaker instance (from application config). */
        private String circuitBreaker = "default";
        /** Name of the Resilience4j Retry instance. */
        private String retry = "default";
        /**
         * HTTP methods eligible for retry.
         * Defaults to idempotent-safe methods.
         */
        private Set<String> retryMethods = new LinkedHashSet<>(Set.of("GET", "HEAD"));
        /** Name of the Resilience4j Bulkhead instance. */
        private String bulkhead = "default";
        /** Request timeout in milliseconds (0 = disabled). */
        private long timeoutMs = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(String circuitBreaker) { this.circuitBreaker = circuitBreaker; }

        public String getRetry() { return retry; }
        public void setRetry(String retry) { this.retry = retry; }

        public Set<String> getRetryMethods() { return retryMethods; }
        public void setRetryMethods(Set<String> retryMethods) {
            if (retryMethods == null || retryMethods.isEmpty()) {
                this.retryMethods = new LinkedHashSet<>();
                return;
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            retryMethods.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .forEach(normalized::add);
            this.retryMethods = normalized;
        }

        public String getBulkhead() { return bulkhead; }
        public void setBulkhead(String bulkhead) { this.bulkhead = bulkhead; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    // ---- observability / metrics sub-config ----

    /**
     * Global observability settings (Micrometer metrics + tracing).
     * <p>
     * Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     observability:
     *       enabled: true
     *       metric-name: http.client.requests
     *       include-url-path: true
     *       log-request-body: false
     * }</pre>
     */
    public static class ObservabilityConfig {

        /** Master switch – set to {@code false} to disable all metrics/tracing. */
        private boolean enabled = true;

        /** Micrometer timer/counter name (default: {@code http.client.requests}). */
        private String metricName = "http.client.requests";

        /**
         * Include the raw URL path as a tag.
         * Disable for high-cardinality path templates with many distinct IDs.
         */
        private boolean includeUrlPath = true;

        /** Log request body in span events (caution: PII / large payloads). */
        private boolean logRequestBody = false;

        /** Log response body in span events (caution: PII / large payloads). */
        private boolean logResponseBody = false;

        private HealthConfig health = new HealthConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public boolean isIncludeUrlPath() { return includeUrlPath; }
        public void setIncludeUrlPath(boolean includeUrlPath) { this.includeUrlPath = includeUrlPath; }

        public boolean isLogRequestBody() { return logRequestBody; }
        public void setLogRequestBody(boolean logRequestBody) { this.logRequestBody = logRequestBody; }

        public boolean isLogResponseBody() { return logResponseBody; }
        public void setLogResponseBody(boolean logResponseBody) { this.logResponseBody = logResponseBody; }

        public HealthConfig getHealth() { return health; }
        public void setHealth(HealthConfig health) {
            this.health = health != null ? health : new HealthConfig();
        }
    }

    /**
     * Settings for
     * {@link io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator}.
     *
     * <p>The indicator computes a per-client error ratio from probe-to-probe deltas
     * on the {@code http.client.requests} timer meters. A client reports DOWN when
     * its delta sample count meets {@link #getMinSamples()} and its error ratio
     * exceeds {@link #getErrorRateThreshold()}; otherwise UP. The overall status is
     * DOWN if any tracked client is DOWN.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     observability:
     *       health:
     *         enabled: true
     *         error-rate-threshold: 0.5
     *         min-samples: 10
     * }</pre>
     */
    public static class HealthConfig {

        /** Master switch for the health indicator. Default {@code true} (active when actuator is on the classpath). */
        private boolean enabled = true;

        /**
         * Error ratio (in [0, 1]) above which a client is reported DOWN. Default
         * {@code 0.5} (50 %) — tuned for "obviously degraded" downstream services.
         */
        private double errorRateThreshold = 0.5;

        /**
         * Minimum sample count (delta of invocations since the previous probe)
         * required to evaluate a client. Avoids noisy DOWN statuses from one or two
         * isolated errors during a quiet window. Default {@code 10}.
         */
        private long minSamples = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getErrorRateThreshold() { return errorRateThreshold; }
        public void setErrorRateThreshold(double errorRateThreshold) { this.errorRateThreshold = errorRateThreshold; }

        public long getMinSamples() { return minSamples; }
        public void setMinSamples(long minSamples) { this.minSamples = minSamples; }
    }

    // ---- global observability config (not per-client) ----

    private ObservabilityConfig observability = new ObservabilityConfig();

    public ObservabilityConfig getObservability() { return observability; }
    public void setObservability(ObservabilityConfig observability) { this.observability = observability; }

    // ---- correlation-id filter config ----

    /**
     * Settings for {@link io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter}.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     correlation-id:
     *       max-length: 128
     * }</pre>
     */
    public static class CorrelationIdConfig {

        /** Upper bound on the accepted correlation-id value length. Values longer than this are rejected. */
        private int maxLength = 128;

        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
    }

    // ---- inbound-headers filter config ----

    /**
     * Settings for
     * {@link io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter}.
     *
     * <p>The inbound-header snapshot stored in the Reactor context (and subsequently
     * logged by {@link io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger})
     * is filtered through the allow-list / deny-list before being stored:
     *
     * <ol>
     *   <li>If {@link #getAllowList()} is non-empty, only headers whose name matches
     *       an entry in the allow-list are captured.</li>
     *   <li>Captured header values whose name matches {@link #getDenyList()} are
     *       replaced with {@code [REDACTED]}.</li>
     * </ol>
     *
     * <p>Defaults: allow-list empty (capture everything), deny-list set to
     * {@link SensitiveHeaders#DEFAULTS} so credentials and session cookies are never
     * stored or logged.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     inbound-headers:
     *       allow-list: [X-Request-Id, X-User-Id]
     *       deny-list:  [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key]
     * }</pre>
     */
    public static class InboundHeadersConfig {

        private Set<String> allowList = new LinkedHashSet<>();
        private Set<String> denyList = defaultDenyList();

        public Set<String> getAllowList() { return allowList; }
        public void setAllowList(Set<String> allowList) { this.allowList = normalize(allowList); }

        public Set<String> getDenyList() { return denyList; }
        public void setDenyList(Set<String> denyList) {
            // Passing {@code null} or an explicit empty list disables redaction entirely,
            // which may be intentional in tightly controlled environments.
            this.denyList = normalize(denyList);
        }

        private static Set<String> defaultDenyList() {
            LinkedHashSet<String> defaults = new LinkedHashSet<>();
            SensitiveHeaders.DEFAULTS.forEach(defaults::add);
            return defaults;
        }

        private static Set<String> normalize(Set<String> input) {
            if (input == null || input.isEmpty()) {
                return new LinkedHashSet<>();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String entry : input) {
                if (entry == null) continue;
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                out.add(trimmed.toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }
}
