package com.idlefish.trade.marketing.activity;

/**
 * 直播秒杀异步结算分发（ITER-F08 / REQ-07）。
 * <p>
 * 下发 {@link SeckillSettleCommand}：优先经 RocketMQ 削峰；失败则依赖 DB 兜底表 + 调度补偿，
 * 保证「缓存预扣 → 异步落库」最终一致且不丢（与 {@code RocketMqDelayServiceImpl} 同源设计）。
 */
public interface SeckillAsyncDispatcher {

    /** 下发结算指令（best-effort，失败不抛异常，由兜底调度保证最终落库）。 */
    void dispatch(SeckillSettleCommand cmd);
}
