package com.idlefish.trade.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 用户合规档案（ITER-U05）：未成年人标记、营销通知开关、关怀模式偏好、当日时长计量。
 *
 * <p>一个用户一条记录（uk_user），缺省语义：{@code minorFlag=0}、{@code marketingNotify=1}、
 * {@code careMode=0}，即「非未成年 + 允许营销通知 + 关闭关怀模式」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_compliance")
public class UserCompliance extends BaseEntity {

    private Long userId;
    /** 是否未成年：0 否 / 1 是。 */
    private Integer minorFlag;
    /** 年龄段：CHILD / TEEN / ADULT / UNKNOWN。 */
    private String ageBand;
    /** 营销类通知开关：0 关闭 / 1 开启（REQ-09，默认开启）。 */
    private Integer marketingNotify;
    /** 关怀模式（适老化：大字号 + 高对比）：0 关闭 / 1 开启（REQ-02，默认关闭）。 */
    private Integer careMode;
    /** 未成年人每日时长上限（分钟），0 表示取默认 40。 */
    private Integer dailyLimitMinutes;
    /** 当日已用时长（分钟），跨日自动清零。 */
    private Integer usedMinutes;
    /** 时长计量归属日期（跨日清零判定）。 */
    private LocalDate activeDate;
}
