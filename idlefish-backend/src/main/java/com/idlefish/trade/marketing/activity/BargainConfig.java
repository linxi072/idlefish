package com.idlefish.trade.marketing.activity;

import lombok.Data;

import java.io.Serializable;

/**
 * 砍价玩法配置（ITER-F08 / REQ-08、REQ-14）。
 * 序列化于 {@code t_marketing_activity.play_config}（JSON）。
 * 金额单位：分（REQ-04）。
 */
@Data
public class BargainConfig implements Serializable {

    /** 原价（分），展示用。 */
    private Long originPriceFen;

    /** 底价（分）：砍价不可低于此值；同时作为 REQ-14 亏损比对基准。 */
    private Long floorPriceFen;

    /** 目标助力人数：达到即生成下单资格。 */
    private int targetAssistCount;

    /** 单刀最小砍价额（分）。 */
    private Long minCutFen;

    /** 单刀最大砍价额（分）。 */
    private Long maxCutFen;

    /** 每刀是否随机：false=均分剩余，true=在 [min,max] 内随机（总额受控）。 */
    private boolean perAssistRandom;
}
