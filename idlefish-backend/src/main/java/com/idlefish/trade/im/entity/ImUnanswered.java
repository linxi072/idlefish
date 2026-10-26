package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 未命中问题池（ITER-F06 / REQ-14 点踩/追问入池）。
 * <p>低置信度未作答、用户点踩「没用」、LLM 试探后仍无法回答的问题统一入池，
 * 供运营补充知识库话术；{@code hitCount} 统计热度，{@code status} 标记处理进度。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_im_unanswered")
public class ImUnanswered extends BaseEntity {

    /** 状态：待处理。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已补充话术。 */
    public static final String STATUS_RESOLVED = "RESOLVED";

    /** 脱敏后的问题原文。 */
    private String question;
    private String intent;
    private Long categoryId;
    private Integer hitCount;
    /** 最近一次出现的会话（定位上下文）。 */
    private String lastConvId;
    private String status;
}
