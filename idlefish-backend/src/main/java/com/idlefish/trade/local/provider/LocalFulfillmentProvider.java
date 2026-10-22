package com.idlefish.trade.local.provider;

import java.util.List;

/**
 * 第三方同城履约能力端口（ITER-F05 / REQ-06、REQ-09）。
 *
 * <p>本系统是同城配送的「下单方 + 轨迹消费方」，不自建车队：
 * 调度、骑手、运单均由第三方服务商提供。因此该端口只有真实 HTTP 实现，
 * 不存在「本地假成功」实现——服务商不可用必须表现为显式的不可用结果。
 *
 * <p>契约纪律（REQ-09）：任何失败都返回带明确原因的结果对象或使用方捕获异常后
 * 抛 {@code Code.LOCAL_DELIVERY_UNAVAILABLE}，禁止静默返回空数据伪装成「暂无轨迹」。
 */
public interface LocalFulfillmentProvider {

    /** 服务商名称（用于日志、轨迹归因与展示）。 */
    String name();

    /**
     * 凭据与地址是否齐备（未配置即不可用）。
     * 该结论直接决定是否允许下单，避免「点了下单才发现没有服务商」。
     */
    boolean configured();

    /** 向第三方下单调度骑手。 */
    DispatchResult dispatch(DispatchCommand cmd);

    /** 拉取第三方轨迹节点；服务商不可用时返回空列表（由上层转换为明确提示）。 */
    List<TrackPoint> fetchTrack(String providerNo);

    /** 取消第三方运单。 */
    boolean cancel(String providerNo);
}
