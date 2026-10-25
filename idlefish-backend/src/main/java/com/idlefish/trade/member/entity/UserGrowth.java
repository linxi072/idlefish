package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户成长值账户（F-13.2 + ITER-F07 深化）：每个用户一行（惰性创建）。
 * <p>
 * 成长值（{@code growthValue}）<b>不可消耗</b>，仅决定会员等级与权益档位（REQ-01）；
 * 可消耗的积分在 {@code marketing.entity.Point}，两者互不折算。
 * </p>
 * <ul>
 *   <li>{@code growthFrozen}：刷分命中后冻结发放通道（REQ-03），封禁期间同样冻结；</li>
 *   <li>{@code lastActiveAt}：最后活跃时间，作为不活跃衰减的时间基准（REQ-05）；</li>
 *   <li>{@code levelEffectiveAt}：当前等级生效时间，跨阈值即时升级时写入（REQ-04）；</li>
 *   <li>{@code decayNotifiedAt}：最近一次「即将衰减」提前通知时间，防重复打扰（REQ-05 提前 7 日）；</li>
 *   <li>{@code totalEarned}：累计获得成长值，与当前值区分，供对账口径使用（REQ-09）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_growth")
public class UserGrowth extends BaseEntity {

    private Long userId;             // 用户 ID（唯一）
    private Integer growthValue;     // 当前成长值（不可消耗）
    private Integer totalEarned;     // 累计获得成长值（对账口径）
    private String levelCode;        // 当前等级编码
    private Integer growthFrozen;    // 0/1 发放通道冻结（REQ-03）
    private LocalDateTime lastActiveAt;      // 最后活跃时间（REQ-05 衰减基准）
    private LocalDateTime levelEffectiveAt;  // 当前等级生效时间（REQ-04）
    private LocalDateTime decayNotifiedAt;   // 最近一次衰减提前通知时间（REQ-05）

    /** 发放通道是否冻结（空值按未冻结处理）。 */
    public boolean isFrozen() {
        return growthFrozen != null && growthFrozen == 1;
    }
}
