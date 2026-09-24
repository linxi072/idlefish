package com.idlefish.trade.common.web;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SimpleRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求限流拦截器（F-04 安全加固·限流骨架）。
 *
 * 按 {@code (clientIp, path)} 固定窗口限流；默认关闭（{@code idlefish.ratelimit.enabled=false}），
 * 生产经 {@code IDLEFISH_RATELIMIT_ENABLED=true} 开启。超限返回 429。
 *
 * 生产建议：替换为 Redis 令牌桶（跨实例共享）或网关层限流；本拦截器作为应用层兜底与本地演示。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final SimpleRateLimiter limiter;

    public RateLimitInterceptor(IdlefishProperties props) {
        IdlefishProperties.RateLimit rl = props.getRatelimit();
        this.enabled = rl.isEnabled();
        this.limiter = new SimpleRateLimiter(rl.getPermitsPerMinute(), 60L);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String key = clientIp(request) + ":" + request.getRequestURI();
        if (!limiter.tryAcquire(key)) {
            response.setStatus(429); // Too Many Requests（SC_TOO_MANY_REQUESTS 在部分 Servlet 版本未提供，直接用字面量）
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
