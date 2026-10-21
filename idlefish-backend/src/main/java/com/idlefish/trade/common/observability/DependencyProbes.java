package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共享依赖探活（T01 生产化）：零 Actuator 依赖，供两类消费者复用：
 * <ul>
 *   <li>进程级可观测端点 {@link ObservabilityController}（{@code @Profile("!micrometer")}）的
 *       {@code /actuator/health} 依赖探活；</li>
 *   <li>Micrometer profile 下的 6 个 {@code HealthIndicator}（K8s liveness/readiness 聚合）。</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>本类位于主源码根（始终编译），不引用任何 Actuator 类型，离线 {@code mvn -o} 构建不受影响；</li>
 *   <li>所有外部探测零 SDK 依赖（纯 {@code java.net.Socket} + 配置），避免引用 offline 构建缺失的
 *       RedisTemplate/RestClient 等类型破坏离线编译；</li>
 *   <li>MySQL 为关键依赖，探活失败返回 {@link ProbeResult#status DOWN}；Redis/ES/RocketMQ/OSS 为可选外部
 *       依赖，不可达返回 {@code DEGRADED}（降级告警，不误杀进程）；短信仅展示配置态。</li>
 * </ul>
 */
@Component
public class DependencyProbes {

    /** TCP 探活连接超时（毫秒）。 */
    private static final int TCP_TIMEOUT_MS = 500;

    private final DataSource dataSource;
    private final IdlefishProperties props;

    public DependencyProbes(DataSource dataSource, IdlefishProperties props) {
        this.dataSource = dataSource;
        this.props = props;
    }

    /** MySQL：关键依赖，执行 {@code SELECT 1} 校验连接池可用性。 */
    public ProbeResult probeMysql() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            return ProbeResult.up("ok");
        } catch (Exception e) {
            return ProbeResult.down(String.valueOf(e.getMessage()));
        }
    }

    /** Redis：系统唯一缓存组件，始终探测 TCP 可达性。 */
    public ProbeResult probeRedis() {
        IdlefishProperties.Redis redis = props.getRedis();
        String host = redis == null ? "127.0.0.1" : redis.getHost();
        int port = redis == null ? 6379 : redis.getPort();
        if (tcpReachable(host, port, TCP_TIMEOUT_MS)) {
            return ProbeResult.up("tcp reachable " + host + ":" + port);
        }
        return ProbeResult.degraded(host + ":" + port + " unreachable");
    }

    /** Elasticsearch：依据 idlefish.search.hosts 做 TCP 探测，未配置视为健康。 */
    public ProbeResult probeElasticsearch() {
        IdlefishProperties.Search search = props.getSearch();
        if (search == null || search.getHosts() == null || search.getHosts().isEmpty()) {
            return ProbeResult.up("not configured");
        }
        String[] hp = parseHostPort(search.getHosts().get(0), 9200);
        if (hp == null) {
            return ProbeResult.up("not configured");
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            return ProbeResult.up("tcp reachable " + hp[0] + ":" + hp[1]);
        }
        return ProbeResult.degraded(hp[0] + ":" + hp[1] + " unreachable");
    }

    /** RocketMQ：依据 idlefish.mq.endpoint 做 TCP 探测，未配置（DB 兜底）视为健康。 */
    public ProbeResult probeRocketMq() {
        IdlefishProperties.Mq mq = props.getMq();
        if (mq == null || mq.getEndpoint() == null || mq.getEndpoint().isBlank()) {
            return ProbeResult.up("not configured (DB fallback)");
        }
        String[] hp = parseHostPort(mq.getEndpoint(), defaultHttpPort(mq.getEndpoint()));
        if (hp == null) {
            return ProbeResult.up("not configured");
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            return ProbeResult.up("tcp reachable " + hp[0] + ":" + hp[1]);
        }
        return ProbeResult.degraded(hp[0] + ":" + hp[1] + " unreachable");
    }

    /** OSS：依据 idlefish.oss.endpoint 做 TCP 探测（默认 443），未配置视为健康。 */
    public ProbeResult probeOss() {
        IdlefishProperties.Oss oss = props.getOss();
        if (oss == null || oss.getEndpoint() == null || oss.getEndpoint().isBlank()) {
            return ProbeResult.up("not configured");
        }
        String[] hp = parseHostPort(oss.getEndpoint(), 443);
        if (hp == null) {
            return ProbeResult.up("not configured");
        }
        if (tcpReachable(hp[0], Integer.parseInt(hp[1]), TCP_TIMEOUT_MS)) {
            return ProbeResult.up("tcp reachable " + hp[0] + ":" + hp[1]);
        }
        return ProbeResult.degraded(hp[0] + ":" + hp[1] + " unreachable");
    }

    /** 短信网关：依据 idlefish.notify.sms.enabled 仅做配置态展示（不主动外呼探测）。 */
    public ProbeResult probeSms() {
        IdlefishProperties.Notify notify = props.getNotify();
        boolean enabled = notify != null && notify.getSms() != null && notify.getSms().isEnabled();
        return ProbeResult.up(enabled ? "enabled" : "disabled");
    }

    // ------------------------- 工具方法（包内/单测可测，零依赖） -------------------------

    /**
     * 纯 TCP 可达性探测，超时/异常均返回 false。
     * <p>声明为 {@code protected} 实例方法（而非 static），便于单测通过匿名子类覆写以固定结果，
     * 不依赖真实网络环境；默认实现即零依赖 Socket 探活。</p>
     */
    protected boolean tcpReachable(String host, int port, int timeoutMs) {
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
    static String[] parseHostPort(String endpoint, int defaultPort) {
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

    /**
     * 探活结果：{@code status} ∈ {UP, DEGRADED, DOWN}，{@code detail} 为可读说明。
     * 不可变，便于在端点与 HealthIndicator 间安全传递。
     */
    public static final class ProbeResult {
        public static final String UP = "UP";
        public static final String DEGRADED = "DEGRADED";
        public static final String DOWN = "DOWN";

        private final String status;
        private final String detail;

        private ProbeResult(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public static ProbeResult up(String detail) {
            return new ProbeResult(UP, detail);
        }

        public static ProbeResult degraded(String detail) {
            return new ProbeResult(DEGRADED, detail);
        }

        public static ProbeResult down(String detail) {
            return new ProbeResult(DOWN, detail);
        }

        public String getStatus() {
            return status;
        }

        public String getDetail() {
            return detail;
        }

        public boolean isUp() {
            return UP.equals(status);
        }

        public boolean isDegraded() {
            return DEGRADED.equals(status);
        }

        public boolean isDown() {
            return DOWN.equals(status);
        }

        /** 转为 LinkedHashMap（保持 key 顺序：status 在前，便于 /actuator/health 阅读）。 */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", status);
            m.put("detail", detail);
            return m;
        }
    }
}
