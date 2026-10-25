package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 成长值流水 VO（ITER-F07 / REQ-09）：来源、规则版本、变更前后值、关联单号全量可见，支持对账。
 */
@Data
public class GrowthValueLogVO {

    private Long id;
    private Long userId;
    private String bizType;       // 来源 code
    private String bizId;         // 关联单号
    private Integer delta;        // 增量（正=获得，负=扣减）
    private Integer beforeValue;  // 变更前成长值
    private Integer afterValue;   // 变更后成长值
    private String levelCode;     // 变更后等级编码
    private String ruleVersion;   // 生效规则版本快照（REQ-14）
    private String remark;        // 说明（含拦截原因）
    private String createdAt;     // 格式化时间
}
