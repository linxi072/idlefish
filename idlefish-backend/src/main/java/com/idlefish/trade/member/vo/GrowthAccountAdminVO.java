package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 成长值账户后台视图（ITER-F07 / PRD §六 运营后台）：刷分名单冻结、流水对账、衰减监控共用。
 */
@Data
public class GrowthAccountAdminVO {

    private Long userId;             // 用户 ID
    private String levelCode;        // 当前等级编码
    private String levelName;        // 当前等级名称（派生）
    private Integer growthValue;     // 当前成长值
    private Integer totalEarned;     // 累计获得成长值
    private Boolean frozen;          // 发放通道冻结状态（REQ-03）
    private String lastActiveAt;     // 最后活跃时间（REQ-05 衰减基准）
    private Integer daysUntilDecay;  // 距进入衰减天数（负数不出现；0=已在衰减期）
}
