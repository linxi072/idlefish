package com.idlefish.trade.notify.channel;

/**
 * 短信渠道接口（Mock/Real 可切换）。
 * 默认 {@link MockSmsNotifyChannelImpl}（仅日志）；生产置 idlefish.notify.sms-mode=real 接入 {@link RealSmsNotifyChannelImpl}。
 */
public interface SmsNotifyChannel extends NotifyChannel {
}
