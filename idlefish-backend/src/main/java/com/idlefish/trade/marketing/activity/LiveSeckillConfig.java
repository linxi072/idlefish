package com.idlefish.trade.marketing.activity;

import lombok.Data;

import java.io.Serializable;

/**
 * 直播秒杀玩法配置（ITER-F08 / REQ-07）。
 * 序列化于 {@code t_marketing_activity.play_config}（JSON）。
 * 秒杀价复用 {@code t_marketing_activity.activity_price}，库存复用 {@code stock}。
 */
@Data
public class LiveSeckillConfig implements Serializable {

    /** 直播间 ID（主播端上架专用库存与口播券）。 */
    private String liveRoomId;

    /** 是否启用缓存预扣 + 异步落库（REQ-07）；关闭则直接走 DB 强一致。 */
    private boolean preDeductEnabled = true;
}
