package com.idlefish.trade.trade.service;

/**
 * 延时队列抽象（PRD §D6 延时任务）：
 * - {@link RocketMqDelayServiceImpl}（真实 RocketMQ，默认启用）：对接阿里云 RocketMQ 延时消息，
 *   DB 作为兜底，消费由 /api/mq/consume 推送触发。
 */
public interface DelayQueueService {

    /**
     * 提交一个延时任务。
     *
     * @param taskType      任务类型（对应 DelayTaskHandler.type）
     * @param bizId         业务主键（如订单号）
     * @param payload       业务参数（JSON 或简单字符串）
     * @param delaySeconds  延时秒数（本地实现据此计算执行时间；RocketMQ 实现映射为延时级别）
     */
    void submit(String taskType, String bizId, String payload, int delaySeconds);

    /**
     * 扫描并执行到期任务（本地实现核心；RocketMQ 模式作为兜底补偿）。
     */
    void scanAndExecute();
}
