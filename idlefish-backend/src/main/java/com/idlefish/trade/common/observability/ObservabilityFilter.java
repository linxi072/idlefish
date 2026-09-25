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
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 零依赖可观测性过滤器：为每次 HTTP 请求注入 traceId 并记录指标。
 * <ul>
 *   <li>traceId：取自请求头 {@code X-Trace-Id}，缺失则生成；写入 MDC 以透传至业务日志，并回写到响应头。</li>
 *   <li>指标：按归一化路由（数字/长哈希片段折叠为 {id}，防维度爆炸）统计请求数与耗时，并记录状态码分布。</li>
 * </ul>
 * 可通过 {@code idlefish.observability.enabled=false} 关闭。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObservabilityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityFilter.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    private static final Pattern NUMERIC = Pattern.compile("\\d+");
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{8,}");

    private final IdlefishProperties props;
    private final MetricsRegistry metrics;

    public ObservabilityFilter(IdlefishProperties props, MetricsRegistry metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (props.getObservability() != null && !props.getObservability().isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }

        String traceId = resolveTraceId(req);
        MDC.put(MDC_KEY, traceId);
        res.setHeader(TRACE_HEADER, traceId);

        String route = normalizeRoute(req);
        long start = System.nanoTime();
        try {
            metrics.increment("http.request." + route);
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            metrics.increment("http.error." + route);
            throw e;
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000L;
            int status = res.getStatus();
            metrics.record("http.latency." + route, millis);
            metrics.increment("http.status." + (status / 100) + "xx");
            log.info("{} {} -> {} ({}ms)", req.getMethod(), route, status, millis);
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest req) {
        String incoming = req.getHeader(TRACE_HEADER);
        if (incoming != null && !incoming.isBlank() && incoming.length() <= 64) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 将 URI 中的 id 片段折叠为 {id}，避免指标维度爆炸。 */
    private String normalizeRoute(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        StringBuilder sb = new StringBuilder();
        for (String part : uri.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append('/').append(isIdLike(part) ? "{id}" : part);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private boolean isIdLike(String part) {
        return NUMERIC.matcher(part).matches() || HASH.matcher(part).matches();
    }
}
