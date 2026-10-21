package com.idlefish.trade.pricing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 估价审计/留痕（ITER-F03）：运营系数覆盖（REQ-08）与异常低价送审（REQ-10）统一落库。
 * bizType 区分：CONFIG_OVERRIDE(系数/配置变更) / ABNORMAL_LOW(异常低价送审) / RECALC(自动重算)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_price_estimate_audit")
public class PriceEstimateAudit extends BaseEntity {

    /** 关联类目 ID（ABNORMAL_LOW 时记录商品所属类目）。 */
    private Long categoryId;
    /** 业务类型：CONFIG_OVERRIDE / ABNORMAL_LOW / RECALC。 */
    private String bizType;
    /** 业务主键：ABNORMAL_LOW→商品 ID；CONFIG_OVERRIDE/RECALC→配置 ID。 */
    private Long bizId;
    /** 变更前快照（JSON）。 */
    private String beforeJson;
    /** 变更后快照（JSON）。 */
    private String afterJson;
    /** 操作人（管理员 ID；系统行为为 0）。 */
    private Long operatorId;
    /** 操作说明/原因。 */
    private String reason;
}
