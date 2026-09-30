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
}
