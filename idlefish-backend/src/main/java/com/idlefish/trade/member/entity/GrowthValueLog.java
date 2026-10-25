package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成长值流水（ITER-F07 / REQ-09 全量留痕可对账）。
 * <p>每次成长值变更（获得 / 扣减 / 冲正 / 衰减）落一条流水，记录：
 * 来源 {@code bizType}、规则版本快照 {@code ruleVersion}（REQ-14 规则变更不追溯重算）、
 * 变更前 {@code beforeValue} / 变更后 {@code afterValue}、关联单号 {@code bizId}、
 * 以及变更后的冻结状态 {@code frozen}。</p>
 * <p>{@code (user_id, biz_type, biz_id)} 唯一索引承担发放幂等兜底：重复发放请求
 * 在并发场景下由唯一索引拦截，杜绝同一订单重复计成长值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_growth_value_log")
public class GrowthValueLog extends BaseEntity {

    private Long userId;          // 用户 ID
    private String bizType;       // 来源 code（EARN_TRADE / DEDUCT_DISPUTE / DECAY ...）
    private String bizId;         // 关联单号（订单号 / 商品 ID / 工单号 / 衰减日期）
    private Integer delta;        // 成长值增量（正=获得，负=扣减）
    private Integer beforeValue;  // 变更前成长值
    private Integer afterValue;   // 变更后成长值
    private String levelCode;     // 变更后等级编码（便于审计等级跃迁）
    private String ruleVersion;   // 生效规则版本快照（REQ-14）
    private Integer frozen;       // 变更后发放通道冻结状态 0/1
    private String remark;        // 说明（命中规则 / 判责原因等）
}
