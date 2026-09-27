package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 用户成长值概览（F-13.2）：运营后台会员列表展示，含派生等级信息。
 */
@Data
public class UserGrowthVO {

    private Long userId;             // 用户 ID
    private Integer growthValue;     // 累计成长值
    private String levelCode;        // 当前等级编码
    private String levelName;        // 当前等级名称（由等级配置派生）
    private String icon;             // 等级图标
    private String color;            // 主题色
}
