package com.idlefish.trade.compliance;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;

/**
 * 个人信息收集授权项（ITER-U05 REQ-07/REQ-08）。
 *
 * <p>每项显式声明：收集目的（purpose）、收集范围（scope）、是否为交易必需（required）。
 * 首次收集前须向用户明示上述三项并征得同意（REQ-07）；用户撤回后须停止基于该授权的处理（REQ-08）。
 */
public enum ConsentItem {

    PHONE("phone", "手机号", "用于账号登录、订单履约联系与异常订单回访", "本机号码，仅用于交易相关联系", true),
    ADDRESS("address", "收货地址", "用于商品发货与同城履约的收件定位", "省市区与详细门牌，仅用于本订单履约", true),
    REAL_NAME("real_name", "实名信息", "用于提现出款、平台介入与未成年人识别", "姓名与身份证号，脱敏存储与展示", false),
    LOCATION("location", "位置信息", "用于同城优先推荐与本地履约派单", "城市级定位，不做精确轨迹留存", false),
    MARKETING_PUSH("marketing_push", "营销推送", "用于优惠券、积分与会员权益的活动通知", "站内信与微信订阅消息，可随时关闭", false);

    private final String code;
    private final String desc;
    private final String purpose;
    private final String scope;
    /** true 表示为完成交易所必需，撤回后相关功能不可用（而非静默继续使用）。 */
    private final boolean required;

    ConsentItem(String code, String desc, String purpose, String scope, boolean required) {
        this.code = code;
        this.desc = desc;
        this.purpose = purpose;
        this.scope = scope;
        this.required = required;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getScope() {
        return scope;
    }

    public boolean isRequired() {
        return required;
    }

    public static ConsentItem fromCode(String code) {
        if (code == null) {
            throw new BizException(Code.PARAM_INVALID, "授权项不能为空");
        }
        for (ConsentItem item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        throw new BizException(Code.PARAM_INVALID, "未知授权项：" + code);
    }
}
