package com.idlefish.trade.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * 可配置风控规则（F-15.1）：替代原 RiskEngine 中硬编码的 R1~R6，
 * 支持 DB 持久化、运营后台热加载与按 type 分派的纯函数判定。
 */
@Data
@TableName("t_risk_rule")
public class RiskRule extends BaseEntity implements Serializable {

    /** 规则码，唯一，如 R1_HIGH_PUBLISH / R3_NEW_DEVICE_PAY。 */
    private String code;
    /** 展示名。 */
    private String name;
    /** 规则类型，见 {@link com.idlefish.trade.risk.service.RiskRuleType}。 */
    private String type;
    /** 默认命中等级（low/mid/high），纯函数可据此派生最终等级。 */
    private String level;
    /** 是否启用：1 启用 / 0 停用。 */
    private int enabled = 1;
    /** 维度：user（用户行为）/ device（设备维度）。 */
    private String scope;
    /** 阈值：计数阈值 / 金额阈值（分）/ 频次阈值，语义随 type 而定。 */
    private long threshold;
    /** 统计窗口（分钟），用于频控类规则。 */
    private int windowMin = 1;
    /** 评估优先级，升序执行。 */
    private int priority;
    /** 规则说明。 */
    private String description;
}
