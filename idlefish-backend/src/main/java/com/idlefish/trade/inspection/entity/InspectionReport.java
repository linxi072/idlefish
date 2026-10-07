package com.idlefish.trade.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 鉴定报告快照（F-02）：机构回传后落库，不可篡改（REQ-03）。
 * 含结构化评级/功能项/瑕疵项，以及机构原始报文 rawJson 作为存证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_inspection_report")
public class InspectionReport extends BaseEntity {

    private String reportNo;           // 报告编号（唯一）
    private String inspectionNo;       // 关联验货单号
    private String orderNo;            // 关联订单号
    private Long itemId;               // 关联商品 ID
    private Long agencyId;             // 鉴定机构 ID
    private String grade;              // 评级：A/B/C/D
    private Boolean pass;              // 是否通过（REQ-03：通过 / 不通过）
    private String funcItems;          // 功能项检测结果（JSON）
    private String flaws;              // 瑕疵项（JSON）
    private String coverImages;        // 报告封面/图（逗号分隔 URL）
    private String rawJson;            // 机构原始回传报文（不可篡改存证）
    private String reportVersion;      // 报告版本，默认 1.0
}
