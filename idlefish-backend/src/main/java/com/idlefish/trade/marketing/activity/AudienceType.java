package com.idlefish.trade.marketing.activity;

/**
 * 活动人群定向类型（ITER-F08 / REQ-13）。
 * 在「活动可见性」与「参与校验」两处同时生效。
 * <ul>
 *   <li>ALL：不限制；</li>
 *   <li>NEW_USER：新客（注册不满 audienceValue 天，基于 t_user.created_at）；</li>
 *   <li>LEVEL：等级门槛（用户 credit_score ≥ audienceValue，作为会员等级代理）；</li>
 *   <li>REALNAME：需实名认证（real_name_verified=1）。</li>
 * </ul>
 * 注：地区定向（REGION）需用户归属地域字段，当前用户模型无该字段，留作后续扩展（PRD §九 合规）。
 */
public enum AudienceType {
    ALL("ALL"),
    NEW_USER("NEW_USER"),
    LEVEL("LEVEL"),
    REALNAME("REALNAME");

    private final String code;

    AudienceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AudienceType of(String code) {
        if (code == null) {
            return ALL;
        }
        for (AudienceType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return ALL;
    }
}
