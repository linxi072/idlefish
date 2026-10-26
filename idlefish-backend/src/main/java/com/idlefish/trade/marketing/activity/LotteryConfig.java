package com.idlefish.trade.marketing.activity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 抽奖玩法配置（ITER-F08 / REQ-09）。
 * 序列化于 {@code t_marketing_activity.play_config}（JSON）。概率需经 {@link LotteryDrawer#validateProbabilities} 校验后入库。
 */
@Data
public class LotteryConfig implements Serializable {

    /** 奖品池（REQ-09 概率可配置且可审计）。 */
    private List<LotteryPrize> prizes;

    /** 配置版本号（留痕，便于审计与回滚）。 */
    private String auditVersion;

    /** 配置人。 */
    private String createdBy;
}
