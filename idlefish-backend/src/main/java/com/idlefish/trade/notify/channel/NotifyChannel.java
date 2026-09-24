package com.idlefish.trade.notify.channel;

/**
 * 通知渠道统一接口。所有实现必须 best-effort：任何异常内部吞掉，绝不向调用方抛出，
 * 避免阻断主业务流程（支付/订单/退款等）。
 */
public interface NotifyChannel {

    /** 渠道类型，用于路由匹配。 */
    ChannelType type();

    /** 发送通知（best-effort）。 */
    void send(NotifyMessage msg);
}
