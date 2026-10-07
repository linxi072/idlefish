package com.idlefish.trade.trust.vo;

import lombok.Data;

/**
 * 商品详情页信任条聚合视图（U02 REQ-01/02/03/08/09/10/11）。
 * 前端首屏横排展示：已验标 / 信用等级 / 交易保障；点击任一弹出说明层。
 */
@Data
public class TrustItemView {

    /** REQ-01 卖家信用摘要（新卖家见 {@link SellerCredit#newSeller}）。 */
    private SellerCredit sellerCredit;

    /** REQ-02 验货标识（仅真实验货通过后展示，与报告强绑定，无报告不展示）。 */
    private InspectionBadge inspectionBadge;

    /** REQ-03/04 交易保障摘要（完整条款见 /api/trust/guarantee-terms）。 */
    private String guaranteeSummary;

    /** REQ-11 保障适用边界（如虚拟/违禁不适用）。 */
    private String guaranteeBoundary;

    /** REQ-09 卖家风控/封禁提示（true 时前端限制下单）。 */
    private RiskFlag riskFlag;

    /** REQ-10 高客单未验货建议验货入口。 */
    private SuggestInspection suggestInspection;

    /** REQ-09 是否限制下单（封禁或高风险）。 */
    private boolean orderRestricted;

    /** 免责声明：信任信息由平台综合计算，仅供参考，不构成交易承诺（REQ-11/09）。 */
    private String disclaimer = "信任信息由平台综合计算得出，仅供参考，不构成交易承诺";

    /** REQ-01 卖家信用摘要。 */
    @Data
    public static class SellerCredit {
        private Integer creditScore;
        private String creditLevel;       // CreditLevel.name()
        private String creditLevelLabel;  // CreditLevel.getDesc()
        private Integer dealCount;
        /** REQ-08：新卖家/零成交且无信用分时展示「暂无信用」，不得显高分。 */
        private boolean newSeller;
    }

    /** REQ-02 验货标识。 */
    @Data
    public static class InspectionBadge {
        /** NONE / INSPECTING / PASSED / REJECTED。 */
        private String status;
        private boolean passed;
        /** REQ-02 报告入口编号（仅 passed 且存在报告时非空）。 */
        private String reportNo;
        private String grade;
    }

    /** REQ-09 风控提示。 */
    @Data
    public static class RiskFlag {
        private boolean blocked;          // User.status == 1
        private boolean underRiskControl; // 存在 high 级风控事件
        private String tip;
    }

    /** REQ-10 高客单建议验货。 */
    @Data
    public static class SuggestInspection {
        private boolean suggested;
        private String reason;
        private String entry; // 验货入口文案
    }
}
