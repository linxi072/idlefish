package com.idlefish.trade.common.lock;

/**
 * 分布式锁键命名（统一前缀，避免各业务散写字符串）。
 */
public final class LockKeyBuilder {

    private static final String PREFIX = "idlefish:lock:";

    private LockKeyBuilder() {
    }

    /** 商品库存锁（下单扣减 / 取消释放共用）。 */
    public static String item(Long itemId) {
        return PREFIX + "item:" + itemId;
    }

    /** 类目树缓存重建锁（跨实例单飞重建，避免多实例并发重建）。 */
    public static String category() {
        return PREFIX + "category:tree";
    }

    /** 微信 access_token 刷新锁（跨实例单飞刷新，避免多实例并发刷新令牌）。 */
    public static String wxSubscribeToken() {
        return PREFIX + "wx:subscribe:token";
    }
}
