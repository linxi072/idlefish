package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-12.5 / T01 依赖健康探活单元测试（纯 Mockito，离线可跑，不触发真实 TCP/MySQL）。
 * <ul>
 *   <li>MySQL 探活：mock DataSource 正常/异常；</li>
 *   <li>可选依赖聚合：local 缓存 + 无外部配置 → 整体 UP；Redis 配置但不可达 → DEGRADED；</li>
 *   <li>host:port 解析：覆盖 scheme/host:port/纯 host 三种形态（纯函数，单测直测）；</li>
 *   <li>探活逻辑统一复用 {@link DependencyProbes}，端点聚合与 HealthIndicator 同源。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservabilityControllerHealthTest {

    @Mock
    private MetricsRegistry metrics;
    @Mock
    private DataSource dataSource;
    @Mock
    private HttpServletResponse response;

    private DependencyProbes probes(DataSource ds, IdlefishProperties props) {
        return new DependencyProbes(ds, props);
    }

    private ObservabilityController controller(MetricsRegistry m, DependencyProbes p) {
        return new ObservabilityController(m, p);
    }

    @Test
    void mysqlDown_whenConnectionFails() {
        // 默认 mock：getConnection 返回 null → createStatement NPE → 捕获为 DOWN
        DependencyProbes probes = probes(dataSource, new IdlefishProperties());
        assertEquals("DOWN", probes.probeMysql().getStatus());
        Map<String, Object> health = controller(metrics, probes).health(response);
        assertEquals("DOWN", health.get("status"));
        // T07：MySQL 不可用 → HTTP 503（触发 k8s 摘流）
        verify(response).setStatus(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> mysql = (Map<String, Object>) ((Map<?, ?>) health.get("dependencies")).get("mysql");
        assertEquals("DOWN", mysql.get("status"));
    }

    @Test
    void mysqlUp_whenSelect1Succeeds() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        assertEquals("UP", probes(dataSource, new IdlefishProperties()).probeMysql().getStatus());
    }

    @Test
    void overallUp_whenMysqlUpAndNoOptionalDeps() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        // 通过匿名子类固定 tcpReachable=true，验证聚合 happy path（隔离真实网络）。
        DependencyProbes probes = new DependencyProbes(dataSource, new IdlefishProperties()) {
            @Override
            protected boolean tcpReachable(String host, int port, int timeoutMs) {
                return true;
            }
        };
        Map<String, Object> health = controller(metrics, probes).health(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");

        assertEquals("UP", health.get("status"));
        verify(response, org.mockito.Mockito.never()).setStatus(anyInt());
        assertEquals("UP", ((Map<?, ?>) deps.get("mysql")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("redis")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("elasticsearch")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("rocketmq")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("oss")).get("status"));
    }

    @Test
    void overallDegraded_whenRedisConfiguredButUnreachable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        DependencyProbes probes = new DependencyProbes(dataSource, new IdlefishProperties()) {
            @Override
            protected boolean tcpReachable(String host, int port, int timeoutMs) {
                return false;
            }
        };
        Map<String, Object> health = controller(metrics, probes).health(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");

        assertEquals("DEGRADED", health.get("status"));
        assertEquals("DEGRADED", ((Map<?, ?>) deps.get("redis")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("mysql")).get("status"));
        verify(response, org.mockito.Mockito.never()).setStatus(anyInt());
    }

    @Test
    void elasticsearchDegraded_whenConfiguredButUnreachable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        IdlefishProperties props = new IdlefishProperties();
        props.getSearch().setHosts(List.of("http://127.0.0.1:9200"));
        DependencyProbes probes = new DependencyProbes(dataSource, props) {
            @Override
            protected boolean tcpReachable(String host, int port, int timeoutMs) {
                return false;
            }
        };
        Map<String, Object> health = controller(metrics, probes).health(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");
        assertEquals("DEGRADED", health.get("status"));
        assertEquals("DEGRADED", ((Map<?, ?>) deps.get("elasticsearch")).get("status"));
        verify(response, org.mockito.Mockito.never()).setStatus(anyInt());
    }

    @Test
    void parseHostPort_variousForms() {
        // scheme + host + port
        String[] a = DependencyProbes.parseHostPort("http://127.0.0.1:9200", 9200);
        assertEquals("127.0.0.1", a[0]);
        assertEquals("9200", a[1]);
        // https scheme default port
        String[] b = DependencyProbes.parseHostPort("https://mq.example.com/path", 443);
        assertEquals("mq.example.com", b[0]);
        assertEquals("443", b[1]);
        // pure host:port
        String[] d = DependencyProbes.parseHostPort("oss-cn-hangzhou.aliyuncs.com:443", 443);
        assertEquals("oss-cn-hangzhou.aliyuncs.com", d[0]);
        assertEquals("443", d[1]);
        // pure host -> default port
        String[] e = DependencyProbes.parseHostPort("oss-cn-hangzhou.aliyuncs.com", 443);
        assertEquals("oss-cn-hangzhou.aliyuncs.com", e[0]);
        assertEquals("443", e[1]);
        // null
        assertNull(DependencyProbes.parseHostPort(null, 443));
    }
}
