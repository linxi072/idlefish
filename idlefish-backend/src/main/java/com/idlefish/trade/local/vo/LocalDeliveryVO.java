package com.idlefish.trade.local.vo;

import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.trade.vo.LogisticsTrack;
import lombok.Data;

import java.util.List;

/**
 * 同城配送详情视图（ITER-F05 / REQ-06）：配送单本体 + 归一化后的轨迹时间轴。
 * 轨迹复用既有 {@code LogisticsTimelineBuilder} 的类型识别，前端沿用物流时间轴组件。
 */
@Data
public class LocalDeliveryVO {

    /** 配送单本体。 */
    private LocalDelivery delivery;

    /** 轨迹节点（最新在前）。 */
    private List<LogisticsTrack> tracks = List.of();

    public static LocalDeliveryVO of(LocalDelivery delivery, List<LogisticsTrack> tracks) {
        LocalDeliveryVO v = new LocalDeliveryVO();
        v.setDelivery(delivery);
        v.setTracks(tracks == null ? List.of() : tracks);
        return v;
    }
}
