package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 会员等级视图（F-13.2）：用户端「我的会员」返回，含当前等级权益与升级进度。
 */
@Data
public class MemberLevelView {

    private String levelCode;          // 当前等级编码
    private String levelName;          // 当前等级名称
    private String icon;               // 等级图标
    private String color;              // 主题色
    private Integer growthValue;       // 当前成长值
    private Integer freeShipping;      // 是否免运费
    private Integer priorityReview;    // 是否优先审核
    private Integer commissionDiscount; // 平台佣金减免（千分比）
    private String nextLevelName;      // 下一级名称（顶级为 null）
    private Integer growthToNext;      // 距下一级所需成长值
    private Integer percent;           // 当前级内进度（0~100）
}
