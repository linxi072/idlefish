package com.idlefish.trade.notify.channel;

/**
 * 用户 openid 解析器：订阅消息需经微信 openid 定向触达，由用户域提供解析能力（避免 notify 反向依赖 user 细节）。
 * 实现方：{@code com.idlefish.trade.user.service.UserService}。
 */
public interface UserOpenidResolver {

    /** 根据用户 ID 解析微信 openid；用户不存在或未取得 openid 时返回 null。 */
    String resolveOpenid(Long userId);
}
