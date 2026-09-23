package com.idlefish.trade.trade.service;

import java.util.List;
import java.util.Map;

/**
 * 物流服务抽象（PRD §D5 物流）：
 * - {@link MockLogisticsServiceImpl}（idlefish.logistics.mock=true 默认）：模拟单号与轨迹，并落库；
 * - {@link RealLogisticsServiceImpl}（idlefish.logistics.mock=false）：对接真实物流商（快递100），不可用时降级模拟。
 */
public interface LogisticService {

    /** 生成物流单号（发货时若用户未提供则由系统生成）。 */
    String createLogistics(String orderNo);

    /** 发货落库初始轨迹（并触发真实推送，若对接）。 */
    void persistShip(String orderNo, String logisticsNo, String company);

    /** 轨迹查询：优先真实物流商，失败降级模拟，并回写 t_logistics。 */
    List<Map<String, String>> track(String logisticsNo);
}
