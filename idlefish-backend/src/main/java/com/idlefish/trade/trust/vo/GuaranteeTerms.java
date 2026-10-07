package com.idlefish.trade.trust.vo;

import lombok.Data;

import java.util.List;

/**
 * 交易保障条款（U02 REQ-03/04/11）：资金托管说明 + 适用边界。
 * 条款版本化管理（PRD 合规要求）：未接入法务评审前为初版文案，仅作展示，上线前需法务复核递增版本。
 */
@Data
public class GuaranteeTerms {

    /** 条款版本（法务评审后递增）。 */
    private String version = "2026-10-01";

    /** 一句话保障摘要（下单确认页展示）。 */
    private String summary;

    /** 完整条款步骤（适用范围、时效、流程）。 */
    private List<String> steps;

    /** 适用场景。 */
    private String applicableScope;

    /** 不适用情形（REQ-11：虚拟商品 / 违禁品等）。 */
    private List<String> exclusions;

    /** 免责与边界提示。 */
    private String note;
}
