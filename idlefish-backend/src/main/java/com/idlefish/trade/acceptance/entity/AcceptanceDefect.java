package com.idlefish.trade.acceptance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 真机验收缺陷（ITER-F01 / REQ-09 缺陷登记回链）。
 * <p>
 * 真机验收发现缺陷时登记本实体，并回链对应 PRD 与用例；修复后重跑对应用例，
 * 状态机 OPEN → FIXED → CLOSED 驱动验收闭环。缺陷可独立存在（不必然绑定某用例）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_acceptance_defect")
public class AcceptanceDefect extends BaseEntity {

    /** 关联验收用例 ID（可为空：部分缺陷不绑定具体用例，仅回链 PRD）。 */
    private Long caseId;

    /** 关联需求编号：REQ-01 ~ REQ-09（与缺陷来源对应）。 */
    private String reqCode;

    /** 缺陷标题。 */
    private String title;

    /** 严重级别：P0 / P1 / P2。 */
    private String severity;

    /** 缺陷状态：OPEN(待修复) / FIXED(已修复待验证) / CLOSED(已闭环)。 */
    private String status;

    /** 回链 PRD 文档标识（默认 {@code PRD-P0-F01-真机验收闭环}）。 */
    private String linkedPrd;

    /** 缺陷描述（复现步骤、影响范围、真机环境信息）。 */
    private String description;

    /** 修复完成时间（FIXED 时回填）。 */
    private LocalDateTime fixedAt;

    /** 闭环时间（CLOSED 时回填）。 */
    private LocalDateTime closedAt;
}
