package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 监控端点安全收敛过滤器（T01，REQ-10 / §九 内网收敛）。
 * <p>
 * 紧随 {@link ObservabilityFilter}（{@code HIGHEST_PRECEDENCE + 1}）执行，对 {@code /actuator/**} 做访问收敛：
 * <ul>
 *   <li>{@code /actuator/health}：始终放行（K8s liveness/readiness 探针、网关探活需要）；</li>
 *   <li>{@code /actuator/prometheus}、{@code /metrics}、{@code /env} 等敏感端点：
 *       仅允许内网 IP（127/10/192.168/172.16~31/169.254 及 IPv6 回环）访问，
 *       或携带 {@code X-Actuator-Token} 且等于 {@code idlefish.observability.actuator-token}（生产建议配置）；</li>
 *   <li>非 {@code /actuator/**} 路径一律放行，不影响业务流量。</li>
 * </ul>
 * 不符合即返回 403，避免指标/环境变量等敏感信息对外暴露。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ActuatorSecurityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ActuatorSecurityFilter.class);

    /** 始终放行的探针端点（K8s 探活需要）。 */
    private static final Set<String> PUBLIC = Set.of("/actuator/health");

    /** 敏感端点：内网/令牌之外禁止访问。 */
    private static final Set<String> SENSITIVE = Set.of(
            "/actuator/prometheus", "/actuator/metrics", "/actuator/env",
            "/actuator/beans", "/actuator/configprops", "/actuator/threaddump",
            "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings");

    private static final String TOKEN_HEADER = "X-Actuator-Token";

    private final IdlefishProperties props;

    public ActuatorSecurityFilter(IdlefishProperties props) {
        this.props = props;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }
        String uri = req.getRequestURI();
        if (allow(req, uri)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("[actuator-security] 拒绝外部访问敏感监控端点 uri={} remoteAddr={}", uri, req.getRemoteAddr());
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"forbidden\",\"message\":\"监控端点仅允许内网或携带 X-Actuator-Token 访问\"}");
    }

    /**
     * 是否放行该请求（抽取为包级可测方法，单测直测）。
     * 规则：非 actuator 路径 / 公开探针 / 内网 IP / 合法令牌 均放行，否则拒绝。
     */
    boolean allow(HttpServletRequest req, String uri) {
        if (uri == null) {
            return true;
        }
        if (!uri.startsWith("/actuator")) {
            return true; // 业务路径不拦截
        }
        if (PUBLIC.contains(uri)) {
            return true; // 探针始终放行
        }
        if (!SENSITIVE.contains(uri) && !uri.startsWith("/actuator/")) {
            return true; // 其它非敏感 actuator 子路径（理论上不存在）兜底放行
        }
        // 敏感端点：令牌优先，其次内网 IP
        String configured = props.getObservability() == null ? null : props.getObservability().getActuatorToken();
        if (configured != null && !configured.isBlank() && configured.equals(req.getHeader(TOKEN_HEADER))) {
            return true;
        }
        return isInternalIp(req.getRemoteAddr());
    }

    /** 判断是否为内网/回环地址（IPv4 私网、链路本地、IPv6 回环及 IPv4-mapped）。 */
    static boolean isInternalIp(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        String a = addr.trim();
        // 去除 IPv4-mapped IPv6 前缀（如 0:0:0:0:0:ffff:127.0.0.1）
        if (a.toLowerCase().startsWith("0:0:0:0:0:ffff:")) {
            a = a.substring("0:0:0:0:0:ffff:".length());
        }
        if (a.equals("0:0:0:0:0:0:0:1") || a.equals("::1")) {
            return true; // IPv6 回环
        }
        if (a.equals("127.0.0.1") || a.startsWith("127.")) {
            return true;
        }
        if (a.startsWith("10.")) {
            return true;
        }
        if (a.startsWith("192.168.")) {
            return true;
        }
        if (a.startsWith("169.254.")) {
            return true; // 链路本地（容器/云内网常见）
        }
        if (a.startsWith("172.")) {
            // 172.16.0.0 ~ 172.31.255.255 为私网
            String[] parts = a.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignore) {
                    return false;
                }
            }
        }
        return false;
    }
}
