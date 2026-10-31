package com.idlefish.trade.compliance.vo;

import com.idlefish.trade.compliance.ConsentItem;
import lombok.Data;

import java.util.List;

/**
 * 合规中心视图：未成年人状态 + 偏好开关 + 授权清单（ITER-U05）。
 *
 * <p>授权清单每项均携带 purpose/scope，前端须在收集前明示（REQ-07）。
 */
@Data
public class ComplianceProfileVO {

    private Long userId;
    /** 是否未成年。 */
    private Boolean minor;
    /** 年龄段：CHILD / TEEN / ADULT / UNKNOWN。 */
    private String ageBand;
    /** 每日时长上限（分钟，未成年人模式有效）。 */
    private Integer dailyLimitMinutes;
    /** 当日已用时长（分钟）。 */
    private Integer usedMinutes;
    /** 营销类通知开关（REQ-09）。 */
    private Boolean marketingNotifyEnabled;
    /** 关怀模式（REQ-02）。 */
    private Boolean careModeEnabled;
    /** 授权清单。 */
    private List<ConsentVO> consents;

    /** 单项授权状态。 */
    @Data
    public static class ConsentVO {
        private String code;
        private String desc;
        /** 收集目的（REQ-07 明示）。 */
        private String purpose;
        /** 收集范围（REQ-07 明示）。 */
        private String scope;
        /** 是否交易必需：撤回后相关功能不可用。 */
        private Boolean required;
        /** 是否已授权；null 表示用户尚未做出选择（沿用历史默认）。 */
        private Boolean granted;
        private String grantedAt;
        private String revokedAt;

        public static ConsentVO of(ConsentItem item, Boolean granted, String grantedAt, String revokedAt) {
            ConsentVO vo = new ConsentVO();
            vo.setCode(item.getCode());
            vo.setDesc(item.getDesc());
            vo.setPurpose(item.getPurpose());
            vo.setScope(item.getScope());
            vo.setRequired(item.isRequired());
            vo.setGranted(granted);
            vo.setGrantedAt(grantedAt);
            vo.setRevokedAt(revokedAt);
            return vo;
        }
    }
}
