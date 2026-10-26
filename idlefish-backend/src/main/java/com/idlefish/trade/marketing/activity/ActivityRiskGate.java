package com.idlefish.trade.marketing.activity;

/**
 * 活动风控钩子（ITER-F08 / REQ-05）。
 * 参与请求经风控校验（设备/频率/账号画像），命中则拒绝参与。
 * 定义为接口便于纯函数服务层以 Mock 注入测试，默认实现 {@link DefaultActivityRiskGate} 委托既有风控引擎。
 */
public interface ActivityRiskGate {

    /**
     * 是否允许该用户参与。
     *
     * @param userId    用户 ID（可空，未登录上下文直接拒绝）
     * @param deviceId  设备指纹（可空）
     * @param activityId 活动 ID
     * @param kind      玩法类型
     * @return true=放行；false=风控命中，拒绝参与
     */
    boolean allow(Long userId, String deviceId, Long activityId, String kind);
}
