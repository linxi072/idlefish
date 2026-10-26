package com.idlefish.trade.analytics.vo;

import lombok.Data;

/**
 * 会员成长值对账报表（ITER-F07 遗留项④ / PRD §12.4）：跨用户 / 账户 / 流水三方聚合，
 * 支撑运营核对「账户存量总成长值」与「流水总账」是否一致（reconciliationDiff 应≈0）。
 */
@Data
public class AnalyticsMemberGrowth {

    /** 注册用户总数（t_user）。 */
    private Long totalUsers;

    /** 已建立成长值账户的用户数（t_user_growth）。 */
    private Long usersWithAccount;

    /** 未建账户的用户数（totalUsers - usersWithAccount），即存量待初始化规模。 */
    private Long usersWithoutAccount;

    /** 账户存量总成长值（SUM t_user_growth.growth_value）。 */
    private Long totalGrowthValue;

    /** 累计获得（流水正向 delta 之和）。 */
    private Long cumulativeEarned;

    /** 累计衰减（biz_type=DECAY 的 ABS delta 之和）。 */
    private Long cumulativeDecay;

    /** 累计判责扣减（biz_type=DEDUCT_DISPUTE 的 ABS delta 之和）。 */
    private Long cumulativeDeductDispute;

    /** 累计冲正（biz_type=DEDUCT_REVERSE 的 ABS delta 之和）。 */
    private Long cumulativeDeductReverse;

    /** 申诉回补（biz_type=RESTORE_DISPUTE 的 ABS delta 之和）。 */
    private Long restored;

    /** 冻结账户数（growth_frozen=1）。 */
    private Long frozenAccounts;

    /** 流水总账（SUM 全部 delta）。 */
    private Long ledgerSum;

    /** 对账差额 = 账户存量总成长值 - 流水总账（应≈0，非 0 提示数据异常）。 */
    private Long reconciliationDiff;
}
