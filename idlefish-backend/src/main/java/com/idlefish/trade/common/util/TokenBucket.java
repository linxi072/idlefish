package com.idlefish.trade.common.util;

/**
 * 令牌桶限流算法（纯函数，F-15.2 分布式限流算法内核）。
 *
 * 与 {@link SimpleRateLimiter} 的固定窗口不同，令牌桶允许突发（桶满时一次性放行至 capacity），
 * 并以恒定速率 {@code ratePerSec} 回填，平滑跨窗口的配额抖动。本类为确定性、无 IO 的纯函数，
 * 便于离线单测；Redis 版（{@link RedisRateLimiter}）的 Lua 脚本与之保持同一套数学，保证单实例行为一致。
 *
 * 不变量：tokens ∈ [0, capacity]；回填量不会因时钟回拨（now < ts）而变负。
 */
public final class TokenBucket {

    private TokenBucket() {
    }

    /** 令牌桶状态：当前令牌数与上次回填时间戳（毫秒）。 */
    public static final class State {
        public final double tokens;
        public final long tsMs;

        public State(double tokens, long tsMs) {
            this.tokens = tokens;
            this.tsMs = tsMs;
        }
    }

    /** 决策结果：是否放行，以及回填后的新状态。 */
    public static final class Decision {
        public final boolean allowed;
        public final State next;

        public Decision(boolean allowed, State next) {
            this.allowed = allowed;
            this.next = next;
        }
    }

    /**
     * 评估一次获取请求。
     *
     * @param state       当前状态；为 null 表示首次，按满桶初始化
     * @param nowMs       当前时间戳（毫秒）
     * @param capacity    桶容量（= 每分钟配额）
     * @param ratePerSec  回填速率（令牌/秒）= 每分钟配额 / 60
     * @param requested   本次请求令牌数（通常为 1）
     * @return 决策（是否放行 + 新状态）
     */
    public static Decision eval(State state, long nowMs, double capacity, double ratePerSec, long requested) {
        double tokens = state == null ? capacity : state.tokens;
        long ts = state == null ? nowMs : state.tsMs;

        double delta = (nowMs - ts) / 1000.0 * ratePerSec;
        if (delta < 0) {
            delta = 0; // 时钟回拨保护：不倒扣
        }
        tokens = tokens + delta;
        if (tokens > capacity) {
            tokens = capacity; // 桶满封顶
        }

        boolean allowed = tokens >= requested;
        if (allowed) {
            tokens = tokens - requested;
        }
        return new Decision(allowed, new State(tokens, nowMs));
    }
}
