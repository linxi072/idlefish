package com.idlefish.trade.im.assistant;

/**
 * IM 智能助手意图枚举（ITER-F06 / PRD-P2-F06）。
 * <p>覆盖售前咨询高频意图；{@link #RULING} 为裁决类诉求（退款/责任/赔付判定），
 * 命中即转人工，禁止自动作答（REQ-04 合规红线）。</p>
 */
public enum ImIntent {

    /** 发货咨询（什么时候发 / 多久到 / 是否包邮）。 */
    SHIPPING("发货咨询"),
    /** 议价咨询（便宜点 / 优惠 / 多少钱）。 */
    PRICE("议价咨询"),
    /** 退换售后（怎么退款 / 退货流程 / 七天无理由）。 */
    REFUND("退换售后"),
    /** 鉴定验货（正品吗 / 支持验货吗 / 成色）。 */
    INSPECTION("鉴定验货"),
    /** 物流查询（到哪了 / 单号 / 签收）。 */
    LOGISTICS("物流查询"),
    /** 闲聊寒暄（你好 / 在吗 / 谢谢）。 */
    CHITCHAT("闲聊寒暄"),
    /** 纠纷吐槽（骗子 / 投诉 / 坑）。 */
    DISPUTE("纠纷吐槽"),
    /** 裁决类诉求：命中即转人工，不自动作答（REQ-04）。 */
    RULING("裁决类诉求"),
    /** 未识别。 */
    UNKNOWN("未识别");

    private final String desc;

    ImIntent(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 是否可进入自动作答链路。裁决类（RULING）一律人工；未识别（UNKNOWN）走知识库未命中/LLM 试探。
     */
    public boolean autoAnswerable() {
        return this != RULING;
    }
}
