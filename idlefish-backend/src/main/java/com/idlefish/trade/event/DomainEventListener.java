package com.idlefish.trade.event;

import java.util.Set;

/**
 * 领域事件监听器（ITER-T06 本地进程内消费入口）。
 * <p>
 * 实现类注册为 Spring Bean 即自动被 {@link EventDispatcher} 发现；
 * 单体架构下事件先走本地监听器（同进程强一致消费），
 * 未来拆分服务时由 MQ 传输层接管（对监听器接口零改动）。
 * <ul>
 *   <li>{@link #interests()}：声明关心的事件类型集合（空集合 = 不消费任何事件）；</li>
 *   <li>{@link #onEvent(DomainEvent)}：消费逻辑，抛出异常视为消费失败（触发重试 / 死信）；</li>
 *   <li>{@link #hasSideEffect()}：是否副作用消费者（REQ-13）：默认 true，
 *       回放流量（{@code DomainEvent.isReplay()}）会跳过本监听器，仅让只读消费者（如刷新缓存/视图）执行。</li>
 * </ul>
 * <p>消费幂等（REQ-07）由 {@code EventConsumeTemplate} 统一提供，
 * 监听器内部建议经模板执行，以 {@code eventId} 为幂等键。</p>
 */
public interface DomainEventListener {

    /** 关心的事件类型集合（不可为 null，可为空集合）。 */
    Set<EventType> interests();

    /** 消费事件。抛出任何异常视为本次消费失败。 */
    void onEvent(DomainEvent event);

    /** 是否副作用消费者（默认 true：回放流量跳过；只读消费者覆写为 false）。 */
    default boolean hasSideEffect() {
        return true;
    }
}
