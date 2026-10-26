package com.idlefish.trade.marketing.activity;

import lombok.Data;

import java.io.Serializable;

/**
 * 直播秒杀异步结算指令（ITER-F08 / REQ-07）。
 * 缓存预扣成功后下发，由 MQ 消费或兜底调度触发 {@code LiveSeckillService.settle} 落库。
 * 经 idempotencyKey 保证最终一致且仅生效一次（REQ-06）。
 */
@Data
public class SeckillSettleCommand implements Serializable {

    private String idempotencyKey;
    private Long userId;
    private Long activityId;
    private String deviceId;
    /** 业务占位（如预占订单号）。 */
    private String bizId;
}
