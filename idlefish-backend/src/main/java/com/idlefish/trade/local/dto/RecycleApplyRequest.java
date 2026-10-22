package com.idlefish.trade.local.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回收申请入参（ITER-F05 / REQ-02、REQ-11）。
 * 金额恒「分」；地址与联系方式在服务端脱敏后落库（PRD §九）。
 */
@Data
public class RecycleApplyRequest {

    /** 关联商品 ID（一键回收场景必填，REQ-11；独立申请可为空）。 */
    private Long itemId;

    /** 类目 ID（回收白名单校验与估价维度）。 */
    private Long categoryId;

    /** 回收品标题快照。 */
    @Size(max = 128)
    private String title;

    /** 成色档位 1~5（缺省 3）。 */
    private Integer conditionLevel;

    /** 参考价（分）：不传时回落到 ITER-F03 估价引擎基准价；两者皆无则拒绝受理（REQ-02 明确失败）。 */
    private Long referencePriceFen;

    /** 取件方式：HOME 上门 / SELF 自送到店（缺省 HOME）。 */
    private String pickupType;

    private String province;
    private String city;
    private String district;

    /** 取件地址（明文入参，服务端脱敏后存储）。 */
    @Size(max = 255)
    private String address;

    @Size(max = 32)
    private String contactName;

    @Size(max = 32)
    private String contactPhone;

    /** 预约上门时间（ISO 字符串，如 2026-10-25T10:00:00）。 */
    private String appointmentAt;
}
