package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能助手知识库话术（ITER-F06 / REQ-11 品类话术优先）。
 * <ul>
 *   <li>{@code categoryId}：0 表示通用话术（全品类回落）；非 0 为品类专属，优先匹配；</li>
 *   <li>{@code intent}：{@link com.idlefish.trade.im.assistant.ImIntent} 枚举名；</li>
 *   <li>{@code keywords}：逗号分隔的触发词（小写比对）；</li>
 *   <li>{@code answerTemplate}：答案模板，金额占位由 {@code AnswerComposer.formatFen} 渲染；</li>
 *   <li>{@code priority}：运营排序权重（同分竞争时生效）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_im_knowledge")
public class ImKnowledge extends BaseEntity {

    /** 通用话术类目占位（品类优先匹配的回落对象）。 */
    public static final long CATEGORY_GENERAL = 0L;
    /** 上架状态。 */
    public static final String STATUS_ON = "ON";
    /** 下架状态。 */
    public static final String STATUS_OFF = "OFF";

    private Long categoryId;
    private String intent;
    private String keywords;
    private String answerTemplate;
    private Integer priority;
    private String status;
    private String remark;
}
