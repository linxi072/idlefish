package com.idlefish.trade.common.web;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.RedisRateLimiter;
import com.idlefish.trade.common.util.SimpleRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求限流拦截器（F-04 安全加固·限流；F-15.2 升级为分布式令牌桶）。
 *
 * 限流策略：默认启用 Redis 令牌桶（{@link RedisRateLimiter}，Lua 原子脚本，跨实例共享配额）；
 * 当 Redis 不可用时降级为内存固定窗口（{@link SimpleRateLimiter}）兜底，避免限流失效或阻断核心链路。
 * 按 {@code (clientIp, path)} 限流；默认关闭（{@code idlefish.ratelimit.enabled=false}），
 * 生产经 {@code IDLEFISH_RATELIMIT_ENABLED=true} 开启。超限返回 429。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final boolean enabled;
    private final RedisRateLimiter redisLimiter;
    private final SimpleRateLimiter localLimiter;

    public RateLimitInterceptor(IdlefishProperties props, RedisTemplate<String, Object> redisTemplate) {
        IdlefishProperties.RateLimit rl = props.getRatelimit();
        this.enabled = rl.isEnabled();
        long permits = rl.getPermitsPerMinute();
        this.redisLimiter = new RedisRateLimiter(redisTemplate, permits);
        this.localLimiter = new SimpleRateLimiter(permits, 60L);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String key = clientIp(request) + ":" + request.getRequestURI();
        boolean allowed;
        try {
            allowed = redisLimiter.tryAcquire(key);
        } catch (RuntimeException ex) {
            // Redis 不可用：降级本地固定窗口兜底（best-effort，不阻断业务）
            log.warn("Redis rate limiter unavailable, fall back to local fixed-window: {}", ex.getMessage());
            allowed = localLimiter.tryAcquire(key);
        }
        if (!allowed) {
            response.setStatus(429); // Too Many Requests
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
