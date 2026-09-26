package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F-12.5 依赖健康探活单元测试（纯 Mockito，离线可跑，不触发真实 TCP/MySQL）。
 * <ul>
 *   <li>MySQL 探活：mock DataSource 正常/异常；</li>
 *   <li>可选依赖聚合：local 缓存 + 无外部配置 → 整体 UP；Redis 配置但不可达 → DEGRADED；</li>
 *   <li>host:port 解析：覆盖 scheme/host:port/纯 host 三种形态。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservabilityControllerHealthTest {

    @Mock
    private MetricsRegistry metrics;
    @Mock
    private DataSource dataSource;

    private ObservabilityController controller() {
        return new ObservabilityController(metrics, dataSource, new IdlefishProperties());
    }

    @Test
    void mysqlDown_whenConnectionFails() throws Exception {
        // 默认 mock：getConnection 返回 null → createStatement NPE → 捕获为 DOWN
        Map<String, Object> health = controller().health();
        assertEquals("DOWN", health.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mysql = (Map<String, Object>) ((Map<?, ?>) health.get("dependencies")).get("mysql");
        assertEquals("DOWN", mysql.get("status"));
    }

    @Test
    void overallUp_whenMysqlUpAndNoOptionalDeps() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        Map<String, Object> health = controller().health();
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");

        assertEquals("UP", health.get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("mysql")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("redis")).get("status")); // local cache
        assertEquals("UP", ((Map<?, ?>) deps.get("elasticsearch")).get("status")); // not configured
        assertEquals("UP", ((Map<?, ?>) deps.get("rocketmq")).get("status")); // DB fallback
        assertEquals("UP", ((Map<?, ?>) deps.get("oss")).get("status")); // not configured
    }

    @Test
    void overallDegraded_whenRedisConfiguredButUnreachable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        IdlefishProperties props = new IdlefishProperties();
        props.getCache().setType("redis");
        ObservabilityController spy = org.mockito.Mockito.spy(new ObservabilityController(metrics, dataSource, props));
        doReturn(false).when(spy).tcpReachable(anyString(), anyInt(), anyInt());

        Map<String, Object> health = spy.health();
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");

        assertEquals("DEGRADED", health.get("status"));
        assertEquals("DEGRADED", ((Map<?, ?>) deps.get("redis")).get("status"));
        assertEquals("UP", ((Map<?, ?>) deps.get("mysql")).get("status"));
    }

    @Test
    void elasticsearchDegraded_whenConfiguredButUnreachable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        IdlefishProperties props = new IdlefishProperties();
        props.getSearch().setHosts(List.of("http://127.0.0.1:9200"));
        ObservabilityController spy = org.mockito.Mockito.spy(new ObservabilityController(metrics, dataSource, props));
        doReturn(false).when(spy).tcpReachable(anyString(), anyInt(), anyInt());

        Map<String, Object> health = spy.health();
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");
        assertEquals("DEGRADED", health.get("status"));
        assertEquals("DEGRADED", ((Map<?, ?>) deps.get("elasticsearch")).get("status"));
    }

    @Test
    void parseHostPort_variousForms() {
        ObservabilityController c = controller();
        // scheme + host + port
        String[] a = c.parseHostPort("http://127.0.0.1:9200", 9200);
        assertEquals("127.0.0.1", a[0]);
        assertEquals("9200", a[1]);
        // https scheme default port
        String[] b = c.parseHostPort("https://mq.example.com/path", 443);
        assertEquals("mq.example.com", b[0]);
        assertEquals("443", b[1]);
        // pure host:port
        String[] d = c.parseHostPort("oss-cn-hangzhou.aliyuncs.com:443", 443);
        assertEquals("oss-cn-hangzhou.aliyuncs.com", d[0]);
        assertEquals("443", d[1]);
        // pure host -> default port
        String[] e = c.parseHostPort("oss-cn-hangzhou.aliyuncs.com", 443);
        assertEquals("oss-cn-hangzhou.aliyuncs.com", e[0]);
        assertEquals("443", e[1]);
        // null
        assertEquals(null, c.parseHostPort(null, 443));
    }
}
