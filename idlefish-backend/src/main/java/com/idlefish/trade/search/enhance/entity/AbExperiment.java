package com.idlefish.trade.search.enhance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AB 实验配置（ITER-F04 搜索推荐深化 / REQ-06 稳定分桶、REQ-07 一键回滚）。
 * <p>
 * 仅承载「实验元信息 + 分桶比例 + 策略参数（JSON）」，不侵入搜索主链路；
 * 搜索增强服务在命中实验时读取 {@code strategyParams} 决定召回/融合加权的偏移量，
 * 回滚时仅将 {@code status} 翻转为 {@code ROLLED_BACK}（幂等），搜索侧一律按对照组行为执行。
 * <p>
 * status 取值：DRAFT（草稿，未生效）/ RUNNING（运行中，参与分桶）/ ROLLED_BACK（已回滚，等同对照）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ab_experiment")
public class AbExperiment extends BaseEntity {

    /** 实验名称（唯一，运营可读，如「搜索融合加权 v1」）。 */
    private String name;

    /** 实验状态：DRAFT / RUNNING / ROLLED_BACK。 */
    private String status;

    /**
     * 实验组流量占比（0~100，整数百分比）。
     * 其余 (100 - treatmentPercent) 流量恒落入对照组，保证对照组始终有基准流量。
     */
    private Integer treatmentPercent;

    /**
     * 策略参数（JSON 字符串），由搜索增强服务解析后施加偏移，例如：
     * {"recallBoost":1.2,"coldStartBonus":600,"semanticRecall":true}。
     * 允许为空，空串表示无额外偏移（等价于对照组）。
     */
    private String strategyParams;

    /** 实验生效起始时间（窗口判定用，空表示立即生效）。 */
    private LocalDateTime startAt;

    /** 实验生效结束时间（窗口判定用，空表示长期生效）。 */
    private LocalDateTime endAt;

    /** 创建人（管理员 ID 或工号，便于审计）。 */
    private String creator;

    /** 实验说明（可选）。 */
    private String description;
}
