package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 营销活动（ITER-F08 统一活动内核）：砍价 / 抽奖 / 直播秒杀共用一张定义表。
 * 玩法特有配置（砍价底价、抽奖奖品池、直播房间等）序列化于 {@code playConfig}（JSON）。
 * 金额单位：分（REQ-04）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_marketing_activity")
public class MarketingActivity extends BaseEntity {

    /** 玩法类型：BARGAIN / LOTTERY / LIVE_SECKILL（见 {@link com.idlefish.trade.marketing.activity.ActivityKind}）。 */
    private String kind;

    /** 活动标题。 */
    private String title;

    /** 关联商品（秒杀/砍价标的）。 */
    private Long itemId;

    /** 基准价 / 秒杀价（分），抽奖玩法可作奖品价值基准。 */
    private Long activityPrice;

    /** 活动库存（营销配额，独立于商品库存）。 */
    private Integer stock;

    /** 已锁定/已售数量（用于进度展示与对账）。 */
    private Integer soldCount;

    /** 每人限购（<=0 表示不限，REQ-03）。 */
    private Integer limitPerUser;

    /** 活动状态：DRAFT / ONGOING / ENDED。 */
    private String status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 人群定向类型：ALL / NEW_USER / LEVEL / REALNAME（REQ-13）。 */
    private String audienceType;

    /** 定向参数：NEW_USER=天数 / LEVEL=最低信用分。 */
    private String audienceValue;

    /** 风控等级：LOW / MID / HIGH（决定风控钩子强度，REQ-05）。 */
    private String riskLevel;

    /** 补贴预算（分），用于 ROI 预算控制（REQ-15）。 */
    private Long subsidyBudgetFen;

    /** 已用补贴（分），原子累加。 */
    private Long subsidyUsedFen;

    /** 成本线（分）：砍价底价低于此值则在配置阶段被阻断（REQ-14）；<=0 表示未设置。 */
    private Long lossFloorFen;

    /** 玩法特有配置（JSON）：砍价/抽奖/直播各自的规则参数。 */
    private String playConfig;
}
