package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可观测性端点（零依赖，替代 Actuator）。
 * <p>
 * 路径置于 {@code /actuator/**}，已在 WebConfig 中放行鉴权，便于 Prometheus/探活直接抓取。
 * 生产环境建议由网关收敛为内网可访问，避免对外暴露指标细节。
 * <p>
 * {@code /actuator/health} 在进程级探活基础上扩展依赖探活（F-12.5）：
 * <ul>
 *   <li>MySQL 为关键依赖，探活失败直接判定整体 {@code DOWN}；</li>
 *   <li>Redis/ES/RocketMQ/OSS 为可选外部依赖，依据配置 + TCP 可达性探测，
 *       不可达时标记 {@code DEGRADED}（降级告警），不误杀进程；</li>
 *   <li>所有外部探测零 SDK 依赖（纯 {@code java.net.Socket} + 配置），
 *       避免引用 offline 构建缺失的 RedisTemplate 等类型破坏离线编译。</li>
 * </ul>
 */
@RestController
@RequestMapping("/actuator")
public class ObservabilityController {

    /** TCP 探活连接超时（毫秒）。 */
    private static final int TCP_TIMEOUT_MS = 500;

    private final MetricsRegistry metrics;
    private final DataSource dataSource;
    private final IdlefishProperties props;

    public ObservabilityController(MetricsRegistry metrics, DataSource dataSource, IdlefishProperties props) {
        this.metrics = metrics;
        this.dataSource = dataSource;
        this.props = props;
    }

    /** 健康检查：进程 + 依赖。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> deps = new LinkedHashMap<>();

        Map<String, Object> mysql = probeMysql();
        deps.put("mysql", mysql);
        deps.put("redis", probeRedis());
        deps.put("elasticsearch", probeElasticsearch());
        deps.put("rocketmq", probeRocketMq());
        deps.put("oss", probeOss());
        deps.put("sms", probeSms());

        boolean mysqlDown = "DOWN".equals(mysql.get("status"));
        boolean anyDegraded = deps.values().stream()
                .map(d -> (Map<?, ?>) d)
                .anyMatch(d -> "DEGRADED".equals(d.get("status")));
        String overall = mysqlDown ? "DOWN" : (anyDegraded ? "DEGRADED" : "UP");

        body.put("status", overall);
        body.put("timestamp", Instant.now().toString());
        Runtime rt = Runtime.getRuntime();
        body.put("jvmFreeMemoryMB", rt.freeMemory() / (1024 * 1024));
        body.put("jvmTotalMemoryMB", rt.totalMemory() / (1024 * 1024));
        body.put("dependencies", deps);
        return body;
    }

    /** 指标快照：计数器与计时器。 */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }

    // ------------------------- 依赖探活 -------------------------

    /** MySQL：关键依赖，执行 {@code SELECT 1} 校验连接池可用性。 */
    private Map<String, Object> probeMysql() {
        Map<String, Object> m = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            m.put("status", "UP");
        } catch (Exception e) {
            m.put("status", "DOWN");
            m.put("detail", String.valueOf(e.getMessage()));
        }
        return m;
    }

    /** Redis：仅当 idlefish.cache.type=redis 时探测 TCP 可达性，否则本地缓存视为 UP。 */
    private Map<String, Object> probeRedis() {
        Map<String, Object> m = new LinkedHashMap<>();
        IdlefishProperties.Redis redis = props.getRedis();
        String type = props.getCache() == null ? "local" : props.getCache().getType();
        if (!"redis".equals(type)) {
            m.put("status", "UP");
            m.put("detail", "local cache (no redis)");
            return m;
        }
        String host = redis == null ? "127.0.0.1" : redis.getHost();
        int port = redis == null ? 6379 : redis.getPort();
        if (tcpReachable(host, port, TCP_TIMEOUT_MS)) {
            m.put("status", "UP");
        } else {
            m.put("status", "DEGRADED");
            m.put("detail", host + ":" + port + " unreachable");
        }
        return m;
    }

    /** Elasticsearch：依据 idlefish.search.hosts 做 TCP 探测，不可达降级。 */
    private Map<String, Object> probeElasticsearch() {
        Map<String, Object> m = new LinkedHashMap<>();
        IdlefishProperties.Search search = props.getSearch();
        if (search == null || search.getHosts() == null || search.getHosts().isEmpty()) {
            m.put("status", "UP");
            m.put("detail", "not configured");
            return m;
        }
        String[] hp = parseHostPort(search.getHosts().get(0), 9200);
        if (hp == null) {
            m.put("status", "UP");
            m.put("detail", "not configured");
            return m;
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            m.put("status", "UP");
        } else {
            m.put("status", "DEGRADED");
            m.put("detail", hp[0] + ":" + hp[1] + " unreachable");
        }
        return m;
    }

    /** RocketMQ：依据 idlefish.mq.endpoint 做 TCP 探测，不可达降级。 */
    private Map<String, Object> probeRocketMq() {
        Map<String, Object> m = new LinkedHashMap<>();
        IdlefishProperties.Mq mq = props.getMq();
        if (mq == null || mq.getEndpoint() == null || mq.getEndpoint().isBlank()) {
            m.put("status", "UP");
            m.put("detail", "not configured (DB fallback)");
            return m;
        }
        String[] hp = parseHostPort(mq.getEndpoint(), defaultHttpPort(mq.getEndpoint()));
        if (hp == null) {
            m.put("status", "UP");
            m.put("detail", "not configured");
            return m;
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            m.put("status", "UP");
        } else {
            m.put("status", "DEGRADED");
            m.put("detail", hp[0] + ":" + hp[1] + " unreachable");
        }
        return m;
    }

    /** OSS：依据 idlefish.oss.endpoint 做 TCP 探测（默认 443），不可达降级。 */
    private Map<String, Object> probeOss() {
        Map<String, Object> m = new LinkedHashMap<>();
        IdlefishProperties.Oss oss = props.getOss();
        if (oss == null || oss.getEndpoint() == null || oss.getEndpoint().isBlank()) {
            m.put("status", "UP");
            m.put("detail", "not configured");
            return m;
        }
        String[] hp = parseHostPort(oss.getEndpoint(), 443);
        if (hp == null) {
            m.put("status", "UP");
            m.put("detail", "not configured");
            return m;
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            m.put("status", "UP");
        } else {
            m.put("status", "DEGRADED");
            m.put("detail", hp[0] + ":" + hp[1] + " unreachable");
        }
        return m;
    }

    /** 短信网关：依据 idlefish.notify.sms.enabled 仅做配置态展示（不主动外呼探测）。 */
    private Map<String, Object> probeSms() {
        Map<String, Object> m = new LinkedHashMap<>();
        IdlefishProperties.Notify notify = props.getNotify();
        boolean enabled = notify != null && notify.getSms() != null && notify.getSms().isEnabled();
        m.put("status", "UP");
        m.put("detail", enabled ? "enabled" : "disabled");
        return m;
    }

    // ------------------------- 工具方法（包内可测） -------------------------

    /** 纯 TCP 可达性探测，超时/异常均返回 false。 */
    boolean tcpReachable(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 endpoint/host 字符串中解析 host 与 port。
     * 支持 {@code http(s)://host:port/path}、纯 {@code host:port}、纯 {@code host}（用 defaultPort）三种形态。
     */
    String[] parseHostPort(String endpoint, int defaultPort) {
        if (endpoint == null) {
            return null;
        }
        String e = endpoint.trim();
        int schemeIdx = e.indexOf("://");
        if (schemeIdx >= 0) {
            e = e.substring(schemeIdx + 3);
        }
        int slash = e.indexOf('/');
        if (slash >= 0) {
            e = e.substring(0, slash);
        }
        if (e.isBlank()) {
            return null;
        }
        int colon = e.lastIndexOf(':');
        String host;
        int port = defaultPort;
        if (colon >= 0 && colon < e.length() - 1) {
            host = e.substring(0, colon);
            try {
                port = Integer.parseInt(e.substring(colon + 1));
            } catch (NumberFormatException ignore) {
                port = defaultPort;
            }
        } else {
            host = e;
        }
        if (host.isBlank()) {
            return null;
        }
        return new String[]{host, String.valueOf(port)};
    }

    /** 依据 scheme 推断 HTTP 默认端口：https→443，否则 80。 */
    private static int defaultHttpPort(String endpoint) {
        return endpoint != null && endpoint.toLowerCase().startsWith("https") ? 443 : 80;
    }
}
