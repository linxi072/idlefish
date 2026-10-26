package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容拦截记录（ITER-F06 / REQ-08 屏蔽词/引流拦截并记风控）。
 * <p>命中站外联系方式（手机号/微信/QQ）或引流话术时留痕；文本经
 * {@link com.idlefish.trade.im.assistant.SensitiveInfoMasker} 脱敏后存储。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_im_content_block")
public class ImContentBlock extends BaseEntity {

    /** 命中类型：站外联系方式。 */
    public static final String TYPE_CONTACT = "CONTACT";
    /** 命中类型：引流/导流话术。 */
    public static final String TYPE_LEAD = "LEAD";

    private String convId;
    private Long userId;
    private String hitType;
    /** 脱敏后的命中文本。 */
    private String matchedText;
    /** 处置：BLOCK_AND_ESCALATE（拦截并转人工）。 */
    private String action;
}
