package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 共享依赖探活单测（T01 生产化）：纯 Mockito / 纯函数，离线可跑。
 * 覆盖 parseHostPort 解析、tcpReachable 覆写、以及各外部依赖探针的关键/可选分支。
 */
class DependencyProbesTest {

    // ---- 工具方法：parseHostPort ----

    @Test
    void parseHostPort_httpScheme() {
        assertArrayEquals(new String[]{"es.internal", "9200"},
                DependencyProbes.parseHostPort("http://es.internal:9200/path?x=1", 9200));
    }

    @Test
    void parseHostPort_httpsSchemeUsesDefaultPortFallback() {
        // endpoint 未带端口 -> 用 defaultPort（此处显式给 443）
        assertArrayEquals(new String[]{"oss.example.com", "443"},
                DependencyProbes.parseHostPort("https://oss.example.com", 443));
    }

    @Test
    void parseHostPort_plainHostPort() {
        assertArrayEquals(new String[]{"127.0.0.1", "6379"},
                DependencyProbes.parseHostPort("127.0.0.1:6379", 6379));
    }

    @Test
    void parseHostPort_plainHostUsesDefault() {
        assertArrayEquals(new String[]{"db", "3306"},
                DependencyProbes.parseHostPort("db", 3306));
    }

    @Test
    void parseHostPort_nullOrBlank() {
        assertEquals(null, DependencyProbes.parseHostPort(null, 8080));
        assertEquals(null, DependencyProbes.parseHostPort("   ", 8080));
    }

    @Test
    void parseHostPort_blankHostAfterScheme() {
        assertEquals(null, DependencyProbes.parseHostPort("http://", 8080));
    }

    // ---- tcpReachable 覆写（固定结果，避免真实网络依赖）----

    private DependencyProbes probesWithTcp(DataSource ds, IdlefishProperties p, boolean up) {
        return new DependencyProbes(ds, p) {
            @Override
            protected boolean tcpReachable(String host, int port, int timeoutMs) {
                return up;
            }
        };
    }

    @Test
    void tcpReachable_overrideFalse() {
        // 默认实现无法在无网络时验证，这里仅确认覆写机制生效（子类返回 false）
        DependencyProbes p = probesWithTcp(mock(DataSource.class), new IdlefishProperties(), false);
        assertFalse(p.tcpReachable("x", 1, 1));
    }

    @Test
    void tcpReachable_overrideTrue() {
        DependencyProbes p = probesWithTcp(mock(DataSource.class), new IdlefishProperties(), true);
        assertTrue(p.tcpReachable("x", 1, 1));
    }

    // ---- probeMysql：关键依赖，DOWN 分支 ----

    @Test
    void probeMysql_downOnSqlException() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));
        DependencyProbes p = new DependencyProbes(ds, new IdlefishProperties());
        DependencyProbes.ProbeResult r = p.probeMysql();
        assertTrue(r.isDown());
        assertEquals(DependencyProbes.ProbeResult.DOWN, r.getStatus());
    }

    @Test
    void probeMysql_upOnSelect1() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.createStatement()).thenReturn(st);
        when(st.execute("SELECT 1")).thenReturn(true);
        DependencyProbes p = new DependencyProbes(ds, new IdlefishProperties());
        DependencyProbes.ProbeResult r = p.probeMysql();
        assertTrue(r.isUp());
    }

    // ---- probeRedis：可选依赖，TCP 不可达 -> DEGRADED（不误杀）----

    @Test
    void probeRedis_up() {
        IdlefishProperties props = new IdlefishProperties();
        props.getRedis().setHost("127.0.0.1");
        props.getRedis().setPort(6379);
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, true);
        assertTrue(p.probeRedis().isUp());
    }

    @Test
    void probeRedis_degradedWhenUnreachable() {
        IdlefishProperties props = new IdlefishProperties();
        props.getRedis().setHost("10.0.0.5");
        props.getRedis().setPort(6379);
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, false);
        DependencyProbes.ProbeResult r = p.probeRedis();
        assertTrue(r.isDegraded());
        assertEquals(DependencyProbes.ProbeResult.DEGRADED, r.getStatus());
    }

    // ---- probeElasticsearch：未配置 -> UP(not configured)；配置后依赖 TCP ----

    @Test
    void probeElasticsearch_notConfigured() {
        // 默认 Search 的 hosts 为空 -> 视为健康
        DependencyProbes p = new DependencyProbes(mock(DataSource.class), new IdlefishProperties());
        assertTrue(p.probeElasticsearch().isUp());
    }

    @Test
    void probeElasticsearch_upWhenReachable() {
        IdlefishProperties props = new IdlefishProperties();
        props.getSearch().getHosts().add("http://127.0.0.1:9200");
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, true);
        assertTrue(p.probeElasticsearch().isUp());
    }

    @Test
    void probeElasticsearch_degradedWhenUnreachable() {
        IdlefishProperties props = new IdlefishProperties();
        props.getSearch().getHosts().add("http://10.0.0.9:9200");
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, false);
        assertTrue(p.probeElasticsearch().isDegraded());
    }

    // ---- probeRocketMq：未配置（DB 兜底）-> UP；配置后依赖 TCP ----

    @Test
    void probeRocketMq_notConfiguredDbFallback() {
        DependencyProbes p = new DependencyProbes(mock(DataSource.class), new IdlefishProperties());
        assertTrue(p.probeRocketMq().isUp());
    }

    @Test
    void probeRocketMq_degradedWhenUnreachable() {
        IdlefishProperties props = new IdlefishProperties();
        props.getMq().setEndpoint("http://10.0.0.8:8080");
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, false);
        assertTrue(p.probeRocketMq().isDegraded());
    }

    // ---- probeOss：未配置 -> UP；配置后依赖 TCP ----

    @Test
    void probeOss_notConfigured() {
        DependencyProbes p = new DependencyProbes(mock(DataSource.class), new IdlefishProperties());
        assertTrue(p.probeOss().isUp());
    }

    @Test
    void probeOss_upWhenReachable() {
        IdlefishProperties props = new IdlefishProperties();
        props.getOss().setEndpoint("https://oss.example.com");
        DependencyProbes p = probesWithTcp(mock(DataSource.class), props, true);
        assertTrue(p.probeOss().isUp());
    }

    // ---- probeSms：仅配置态展示，不主动外呼 ----

    @Test
    void probeSms_disabledByDefault() {
        DependencyProbes p = new DependencyProbes(mock(DataSource.class), new IdlefishProperties());
        DependencyProbes.ProbeResult r = p.probeSms();
        assertTrue(r.isUp());
        assertEquals("disabled", r.getDetail());
    }

    @Test
    void probeSms_enabled() {
        IdlefishProperties props = new IdlefishProperties();
        props.getNotify().getSms().setEnabled(true);
        DependencyProbes p = new DependencyProbes(mock(DataSource.class), props);
        assertEquals("enabled", p.probeSms().getDetail());
    }

    // ---- ProbeResult.toMap 结构 ----

    @Test
    void probeResult_toMapKeepsOrder() {
        DependencyProbes.ProbeResult r = DependencyProbes.ProbeResult.down("boom");
        assertEquals(DependencyProbes.ProbeResult.DOWN, r.toMap().get("status"));
        assertEquals("boom", r.toMap().get("detail"));
    }
}
