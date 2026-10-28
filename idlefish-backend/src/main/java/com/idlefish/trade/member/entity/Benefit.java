package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 权益目录（ITER-F07 / REQ-07/12/13）：运营配置的可兑换权益。
 * <p>
 * <b>消耗的是积分（可消耗），成长值只作等级门槛</b>——严格遵循 REQ-01 职责分离：
 * 成长值不可消耗、仅决定等级；用户用积分兑换权益，权益再发放为券等实物激励。
 * </p>
 * <ul>
 *   <li>{@code costPoints}：所需积分；</li>
 *   <li>{@code minLevelCode}：最低等级门槛（按等级阈值比较，REQ-07）；</li>
 *   <li>{@code voucherAmountFen}：金额类权益面额，单位「分」（REQ-13）；</li>
 *   <li>{@code stock} / {@code totalStock}：库存，兑换走原子扣减 + 校验双保险（REQ-08）；</li>
 *   <li>{@code dailyLimit}：每用户每日兑换上限，0 表示不限（REQ-08）；</li>
 *   <li>{@code onlineAt} / {@code offlineAt}：定时上下架时间窗，过期自动回收（REQ-12）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_benefit")
public class Benefit extends BaseEntity {

    /** 权益类型：券（走 F-10 发放链路）。 */
    public static final String TYPE_COUPON = "COUPON";
    /** 权益类型：金额类权益（手续费折扣 / 抵扣券，面额以「分」计）。 */
    public static final String TYPE_VOUCHER = "VOUCHER";
    /** 权益类型：积分加赠包。 */
    public static final String TYPE_POINT_PACKAGE = "POINT_PACKAGE";
    /** 权益类型：服务类权益（专属客服 / 曝光加权等，仅记录不自动发放）。 */
    public static final String TYPE_SERVICE = "SERVICE";

    /** 上架状态。 */
    public static final String STATUS_ON_SHELF = "ON_SHELF";
    /** 下架状态。 */
    public static final String STATUS_OFF_SHELF = "OFF_SHELF";

    @NotBlank(message = "权益编码不可为空")
    private String benefitCode;        // 权益编码（唯一，运营侧引用）
    @NotBlank(message = "权益名称不可为空")
    private String benefitName;        // 权益名称
    @NotBlank(message = "权益类型不可为空")
    private String benefitType;        // COUPON / VOUCHER / POINT_PACKAGE / SERVICE
    private String description;        // 权益说明（使用条件 / 有效期，合规要求明示）
    private Long costPoints;           // 所需积分（可消耗）
    private String minLevelCode;       // 最低等级门槛（成长值决定，REQ-01/07）
    private Long couponId;             // 券模板 ID（COUPON 类型必填，走 CouponService.grantCoupon）
    private Long voucherAmountFen;     // 金额类权益面额（分，REQ-13）
    private Integer stock;             // 剩余库存
    private Integer totalStock;        // 总库存（运营参考）
    private Integer dailyLimit;        // 每用户每日兑换上限，0=不限（REQ-08）
    private String status;             // ON_SHELF / OFF_SHELF
    private LocalDateTime onlineAt;    // 上架时间（空=不限制）
    private LocalDateTime offlineAt;   // 下架时间（空=不限制，到期自动回收 REQ-12）
    private Integer sortOrder;         // 展示排序
}
