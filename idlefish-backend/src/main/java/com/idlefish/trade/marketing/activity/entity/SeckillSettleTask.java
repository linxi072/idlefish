package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 直播秒杀结算兜底任务（ITER-F08 / REQ-07）。
 * MQ 发送失败时由 {@code SeckillSettleRecovery} 调度补偿，保证「缓存预扣 → 异步落库」最终一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_seckill_settle_task")
public class SeckillSettleTask extends BaseEntity {

    private String taskType;
    private String bizId;
    /** 结算指令 JSON（{@link com.idlefish.trade.marketing.activity.SeckillSettleCommand}）。 */
    private String payload;
    /** 状态：pending / processing / done / failed。 */
    private String status;
    private LocalDateTime nextExecuteAt;
    private Integer retryCount;
}
