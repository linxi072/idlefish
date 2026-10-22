package com.idlefish.trade.search.serving;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 零依赖熔断器（ITER-T04 / REQ-08）：closed → open → half-open 状态机，
 * 防止搜推服务（远程模式）超时/异常雪崩传导至交易主链路。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>状态迁移核心为<b>纯函数</b> {@link #next(State, boolean, int, int)}，无时钟依赖，可单测固化；</li>
 *   <li>{@link #allowRequest()} 负责 open → half-open 的时间窗口迁移（半开放行单次探测）；</li>
 *   <li>线程安全：状态与计数器均为 volatile / Atomic；探测并发由 synchronized 串行化；</li>
 *   <li>复位 {@link #reset()} 供运维在故障恢复后立即闭合，无需等待窗口。</li>
 * </ul>
 */
public class CircuitBreaker {

    /** 熔断器状态。 */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMs;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long openedAtMs = 0L;

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        // 下限 100ms：生产默认 10s；留出短窗口便于单测验证半开迁移
        this.openDurationMs = Math.max(100L, openDurationMs);
    }

    /**
     * 纯函数状态迁移（REQ-08 核心逻辑，供单测与文档化）：
     * <ul>
     *   <li>HALF_OPEN：探测成功 → CLOSED（恢复），失败 → OPEN（再次熔断）；</li>
     *   <li>CLOSED：失败累计达到阈值 → OPEN，否则保持；成功保持 CLOSED；</li>
     *   <li>OPEN：防御性处理——成功 → CLOSED，失败保持 OPEN（该状态下请求已被拒绝，正常不会记录）。</li>
     * </ul>
     *
     * @param current    当前状态
     * @param success    本次观测是否成功
     * @param failures   本次失败后的累计失败次数
     * @param threshold  熔断阈值
     */
    static State next(State current, boolean success, int failures, int threshold) {
        if (current == State.HALF_OPEN) {
            return success ? State.CLOSED : State.OPEN;
        }
        if (success) {
            return State.CLOSED;
        }
        return failures >= threshold ? State.OPEN : State.CLOSED;
    }

    /**
     * 是否放行一次调用：
     * CLOSED 放行；OPEN 在持续时间窗口结束后迁移 HALF_OPEN 并放行单次探测；
     * HALF_OPEN 放行（探测结果由 recordSuccess/recordFailure 收敛）。
     */
    public boolean allowRequest() {
        State s = state;
        if (s == State.CLOSED) {
            return true;
        }
        if (s == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMs >= openDurationMs) {
                synchronized (this) {
                    if (state == State.OPEN
                            && System.currentTimeMillis() - openedAtMs >= openDurationMs) {
                        state = State.HALF_OPEN;
                    }
                    return state != State.OPEN;
                }
            }
            return false;
        }
        return true; // HALF_OPEN：放行探测
    }

    /** 记录一次成功：状态按纯函数收敛，清零失败计数。 */
    public void recordSuccess() {
        synchronized (this) {
            state = next(state, true, 0, failureThreshold);
            failures.set(0);
            openedAtMs = 0L;
        }
    }

    /** 记录一次失败：累计失败达阈值即熔断（记录打开时刻）。 */
    public void recordFailure() {
        synchronized (this) {
            int f = failures.incrementAndGet();
            State n = next(state, false, f, failureThreshold);
            if (n == State.OPEN && state != State.OPEN) {
                openedAtMs = System.currentTimeMillis();
            }
            state = n;
        }
    }

    /** 当前状态快照。 */
    public State state() {
        return state;
    }

    /** 累计失败次数快照。 */
    public int failures() {
        return failures.get();
    }

    /** 运维复位：立即闭合（故障恢复后免等待窗口）。 */
    public void reset() {
        synchronized (this) {
            state = State.CLOSED;
            failures.set(0);
            openedAtMs = 0L;
        }
    }
}
