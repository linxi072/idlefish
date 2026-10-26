package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 纠纷预警记录（ITER-F06 / REQ-06 纠纷信号预警 + 提示一键发起 F-17 维权）。
 * <ul>
 *   <li>{@code riskScore}：累计风险分（封顶 100）；</li>
 *   <li>{@code level}：WARN / HIGH；</li>
 *   <li>{@code signals}：命中信号（逗号分隔，如 ABUSE,FAKE_ACCUSE）；</li>
 *   <li>{@code handledFlag}：人工是否已跟进（0/1）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_im_dispute_warning")
public class ImDisputeWarning extends BaseEntity {

    /** 预警等级：提醒。 */
    public static final String LEVEL_WARN = "WARN";
    /** 预警等级：严重。 */
    public static final String LEVEL_HIGH = "HIGH";

    private String warningNo;
    private String convId;
    private Long itemId;
    private Long buyerId;
    private Long sellerId;
    private Integer riskScore;
    private String level;
    private String signals;
    /** 会话内下发给买家的提示卡文案（含一键发起维权入口说明）。 */
    private String noticeText;
    private Integer handledFlag;
    private Long handledBy;
}
