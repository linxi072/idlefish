package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员等级配置（F-13.2）：成长值阈值 + 等级权益。
 * 由运营后台维护（AdminMemberLevelController）；应用启动时若表为空则种子默认 5 级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_member_level")
public class MemberLevel extends BaseEntity {

    private String levelCode;        // 等级编码（唯一，如 L1..L5）
    private String levelName;        // 等级名称
    private Integer minGrowth;       // 达到该等级所需成长值（阈值）
    private Integer freeShipping;    // 0/1 是否免运费
    private Integer priorityReview;  // 0/1 是否优先审核
    private Integer commissionDiscount; // 平台佣金减免（千分比，0=不减免）
    private String icon;             // 前端展示图标
    private String color;            // 主题色
    private Integer sortOrder;       // 展示排序
}
