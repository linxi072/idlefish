package com.idlefish.trade.trade.service;

/**
 * 延时任务处理器：每个业务动作实现一个 handler，按 {@link #type()} 注册到延时队列。
 */
public interface DelayTaskHandler {

    /** 任务类型（与 DelayQueueService.submit 的 taskType 对应）。 */
    String type();

    /** 执行业务动作。bizId 通常为订单号；payload 为附加参数。 */
    void handle(String bizId, String payload);
}
