package com.idlefish.trade.notify.channel;

/**
 * 通知触达渠道类型。
 * <ul>
 *   <li>{@link #IN_APP}：站内信（落库 t_notification，用户打开 App/PC 可见）。</li>
 *   <li>{@link #PUSH}：实时 WebSocket 推送（复用 IM 的 WsSessionManager，向在线用户即时推送，离线静默）。</li>
 *   <li>{@link #SMS}：短信（Mock/Real 可切换，默认 Mock，生产置 idlefish.notify.sms-mode=real 接入真实短信）。</li>
 * </ul>
 */
public enum ChannelType {
    IN_APP,
    PUSH,
    SMS,
    /** 微信订阅消息（真实推送，F-14.4）：按 NotificationType→templateId 映射在关键节点触达。 */
    SUBSCRIBE
}
