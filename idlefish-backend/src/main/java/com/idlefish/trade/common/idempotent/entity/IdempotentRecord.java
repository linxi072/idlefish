package com.idlefish.trade.common.idempotent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录（三态：PROCESSING / SUCCESS / FAILED）。
 * <p>
 * 依赖 {@code uk_idem_key} 唯一索引作为**终局防重屏障**——与项目既有的
 * {@code uk_user_coupon}（券限领）同一范式：并发下只有一个请求能插入成功。
 */
@Data
@TableName("t_idempotent_record")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：{bizType}:{bizKey}；超长时取 SHA-256 摘要以控制索引体积。 */
    private String idemKey;

    /** 业务类型（如 order.create / refund.apply / withdraw.apply）。 */
    private String bizType;

    /** 状态：见 {@link com.idlefish.trade.common.idempotent.IdempotentStatus}。 */
    private String status;

    /** 首次成功结果快照（JSON），用于重复请求回放。 */
    private String result;

    /** 执行次数（超时接管会递增）。 */
    private Integer attempt;

    /** PROCESSING 过期时刻：超期后允许被其他请求接管。 */
    private LocalDateTime expireAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
