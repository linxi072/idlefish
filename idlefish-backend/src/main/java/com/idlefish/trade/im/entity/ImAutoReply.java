package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能助手自动应答记录（ITER-F06 / REQ-10 来源标记可审计、REQ-14 点踩/追问入未命中池）。
 * <ul>
 *   <li>{@code source}：KNOWLEDGE（知识库）/ TEMPLATE_FALLBACK（通用模板降级）/ LLM（模型生成）；</li>
 *   <li>{@code feedback}：NONE / USEFUL / USELESS（用户端「有用/没用」反馈）；</li>
 *   <li>{@code escalated}：本次应答是否伴随转人工（0/1）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_im_auto_reply")
public class ImAutoReply extends BaseEntity {

    /** 来源：平台知识库话术。 */
    public static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";
    /** 来源：通用模板（LLM 不可用降级，REQ-12）。 */
    public static final String SOURCE_TEMPLATE_FALLBACK = "TEMPLATE_FALLBACK";
    /** 来源：大模型生成。 */
    public static final String SOURCE_LLM = "LLM";
    /** 来源：转人工提示（升级通知，非知识应答）。 */
    public static final String SOURCE_ESCALATION = "ESCALATION";

    /** 反馈：未评价。 */
    public static final String FEEDBACK_NONE = "NONE";
    /** 反馈：有用。 */
    public static final String FEEDBACK_USEFUL = "USEFUL";
    /** 反馈：没用（点踩，入未命中池 REQ-14）。 */
    public static final String FEEDBACK_USELESS = "USELESS";

    private String replyNo;
    private String convId;
    private Long itemId;
    private Long buyerId;
    private Long sellerId;
    /** 触发本次应答的买家消息 ID。 */
    private Long triggerMessageId;
    private String intent;
    private String source;
    /** 匹配置信度（0~1）。 */
    private Double confidence;
    private String content;
    /** 触发问题的脱敏原文（反馈「没用」时定位入池，REQ-14）。 */
    private String question;
    /** 升级时的会话摘要（REQ-07，仅 escalated=1 有值）。 */
    private String summary;
    private String feedback;
    /** 是否伴随转人工（0/1）。 */
    private Integer escalated;
    private String escalateReason;
}
