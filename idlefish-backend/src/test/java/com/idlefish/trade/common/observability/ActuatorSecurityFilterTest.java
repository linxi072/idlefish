package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 监控端点安全收敛过滤器单测（T01，REQ-10 内网收敛）：直测包级 allow() 与静态 isInternalIp()。
 * 纯 Mockito，离线可跑，不依赖 Spring 上下文。
 */
class ActuatorSecurityFilterTest {

    private static final String TOKEN = "X-Actuator-Token";

    private ActuatorSecurityFilter filter(boolean tokenSet) {
        IdlefishProperties props = new IdlefishProperties();
        if (tokenSet) {
            props.getObservability().setActuatorToken("secret-token");
        }
        return new ActuatorSecurityFilter(props);
    }

    private HttpServletRequest req(String uri, String remoteAddr, String headerToken) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRequestURI()).thenReturn(uri);
        when(r.getRemoteAddr()).thenReturn(remoteAddr);
        when(r.getHeader(TOKEN)).thenReturn(headerToken);
        return r;
    }

    // ===== allow() =====

    @Test
    void allow_businessPathAlwaysPass() {
        ActuatorSecurityFilter f = filter(false);
        assertTrue(f.allow(req("/api/orders", "203.0.113.5", null), "/api/orders"));
    }

    @Test
    void allow_healthPublicAlwaysPass() {
        // /actuator/health 即使外网 IP 也放行（K8s 探针）
        ActuatorSecurityFilter f = filter(false);
        assertTrue(f.allow(req("/actuator/health", "203.0.113.5", null), "/actuator/health"));
    }

    @Test
    void allow_sensitiveExternalIpNoTokenDenied() {
        ActuatorSecurityFilter f = filter(false);
        assertFalse(f.allow(req("/actuator/prometheus", "203.0.113.5", null), "/actuator/prometheus"));
    }

    @Test
    void allow_sensitiveInternalIpPass() {
        ActuatorSecurityFilter f = filter(false);
        assertTrue(f.allow(req("/actuator/prometheus", "10.0.0.3", null), "/actuator/prometheus"));
        assertTrue(f.allow(req("/actuator/metrics", "192.168.1.10", null), "/actuator/metrics"));
    }

    @Test
    void allow_sensitiveExternalIpWithTokenPass() {
        ActuatorSecurityFilter f = filter(true);
        assertTrue(f.allow(req("/actuator/env", "203.0.113.5", "secret-token"), "/actuator/env"));
    }

    @Test
    void allow_sensitiveExternalIpWithWrongTokenDenied() {
        ActuatorSecurityFilter f = filter(true);
        assertFalse(f.allow(req("/actuator/env", "203.0.113.5", "wrong"), "/actuator/env"));
    }

    @Test
    void allow_sensitiveNoTokenConfiguredExternalDenied() {
        // token 未配置时，外网访问敏感端点仍拒绝（仅依赖内网收敛）
        ActuatorSecurityFilter f = filter(false);
        assertFalse(f.allow(req("/actuator/beans", "8.8.8.8", null), "/actuator/beans"));
    }

    // ===== isInternalIp() =====

    @Test
    void isInternalIp_loopbackAndPrivate() {
        assertTrue(ActuatorSecurityFilter.isInternalIp("127.0.0.1"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("127.0.0.1")); // 已覆盖，保持可读性
        assertTrue(ActuatorSecurityFilter.isInternalIp("10.1.2.3"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("192.168.0.1"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("169.254.1.1"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("172.16.0.1"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("172.31.255.255"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("::1"));
        assertTrue(ActuatorSecurityFilter.isInternalIp("0:0:0:0:0:ffff:127.0.0.1"));
    }

    @Test
    void isInternalIp_externalRejected() {
        assertFalse(ActuatorSecurityFilter.isInternalIp("8.8.8.8"));
        assertFalse(ActuatorSecurityFilter.isInternalIp("172.15.0.1")); // 非私网 172 段
        assertFalse(ActuatorSecurityFilter.isInternalIp("172.32.0.1")); // 超出 31
        assertFalse(ActuatorSecurityFilter.isInternalIp(""));
        assertFalse(ActuatorSecurityFilter.isInternalIp(null));
    }
}
