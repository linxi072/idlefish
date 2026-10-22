package com.idlefish.trade.local;

import java.util.Collection;
import java.util.List;

/**
 * 同城履约规则纯函数（ITER-F05）：履约方式、城市/品类可用性、配送计费、自提时效。
 * 无状态、无 IO，离线可单测；所有金额为「分」、距离为「公里」。
 *
 * <p>REQ-09 纪律：任何「不可用」判定都必须返回明确结论交由调用方抛业务异常，
 * 本类内部不做兜底假成功（不得返回 Mock 运单号或伪装可用）。
 */
public final class LocalFulfillmentRules {

    /** 履约方式：普通快递（跨城主流程，不在本迭代改动范围）。 */
    public static final String DELIVERY_EXPRESS = "EXPRESS";
    /** 履约方式：同城自提（REQ-01 商品侧标识、REQ-10 当面验货并缩短确认收货时效）。 */
    public static final String DELIVERY_SELF_PICKUP = "SELF_PICKUP";
    /** 履约方式：同城配送（REQ-06 第三方承接并回传轨迹）。 */
    public static final String DELIVERY_LOCAL = "LOCAL_DELIVERY";

    /** 取件方式：上门取件。 */
    public static final String PICKUP_HOME = "HOME";
    /** 取件方式：用户自送到店。 */
    public static final String PICKUP_SELF = "SELF";

    /** 脱敏后的电话掩码保留位数前/后（如 138****1234）。 */
    public static final int PHONE_HEAD_KEEP = 3;
    public static final int PHONE_TAIL_KEEP = 4;

    private LocalFulfillmentRules() {
    }

    /** 是否为合法履约方式。 */
    public static boolean validDeliveryType(String type) {
        return DELIVERY_EXPRESS.equals(type) || DELIVERY_SELF_PICKUP.equals(type) || DELIVERY_LOCAL.equals(type);
    }

    /** 是否同城类履约（自提或同城配送）。 */
    public static boolean isLocal(String type) {
        return DELIVERY_SELF_PICKUP.equals(type) || DELIVERY_LOCAL.equals(type);
    }

    /**
     * 城市是否开通同城服务（REQ-01 / REQ-09）。
     * 白名单为空表示不限制（开发环境友好，生产显式配置后可形成明确边界）。
     * 白名单中只允许非空白项，配置为 {@code [""]} 时按不限制处理，避免误伤全部城市。
     */
    public static boolean isCitySupported(String city, Collection<String> supportedCities) {
        if (city == null || city.isBlank()) {
            return false;
        }
        if (supportedCities == null) {
            return true;
        }
        boolean hasEffective = false;
        for (String c : supportedCities) {
            if (c == null || c.isBlank()) {
                continue;
            }
            hasEffective = true;
            if (city.equals(c) || city.startsWith(c) || c.startsWith(city)) {
                return true;
            }
        }
        return !hasEffective;
    }

    /**
     * 品类是否在回收白名单（REQ-11 一键回收入口）。
     * 白名单为空表示不限制；白名单中若存在 {@code null} 元素会被忽略。
     */
    public static boolean isCategoryRecyclable(Long categoryId, List<Long> whitelist) {
        if (categoryId == null) {
            return false;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        boolean hasEffective = false;
        for (Long id : whitelist) {
            if (id == null) {
                continue;
            }
            hasEffective = true;
            if (id.equals(categoryId)) {
                return true;
            }
        }
        return !hasEffective;
    }

    /** 配送距离是否在可承接范围内（REQ-09 超范围须明确不可用）。 */
    public static boolean isDistanceSupported(Double distanceKm, double maxKm) {
        if (distanceKm == null || distanceKm < 0) {
            return false;
        }
        return distanceKm <= maxKm;
    }

    /**
     * 同城配送运费（分）：起步价含 {@code baseKm} 公里，超出部分按 {@code perKmFen} 累加，
     * 超出公里数向上取整（不足 1 公里按 1 公里计），保证司机侧收益不被浮点截断。
     *
     * @return 运费（分）；距离为空或超出范围返回 0，调用方须先过 {@link #isDistanceSupported}
     */
    public static long calcFreight(Double distanceKm, long baseFeeFen, double baseKm, long perKmFen) {
        if (distanceKm == null || distanceKm <= 0 || baseKm < 0 || perKmFen < 0) {
            return baseFeeFen < 0 ? 0L : baseFeeFen;
        }
        long fee = baseFeeFen;
        double over = distanceKm - baseKm;
        if (over > 0) {
            long units = (long) Math.ceil(over);
            fee += units * perKmFen;
        }
        return fee;
    }

    /**
     * 自动确认收货天数（REQ-10）：自提当面验货，时效显著短于快递；
     * 其余履约方式沿用平台统一的快递时效。
     *
     * @param deliveryType      履约方式
     * @param selfPickupDays    自提自动确认天数（配置 idlefish.local.self-pickup-confirm-days）
     * @param expressDefaultDays 快递默认天数（10 天，与 OrderService.autoConfirmReceive 对齐）
     */
    public static int confirmDays(String deliveryType, int selfPickupDays, int expressDefaultDays) {
        if (DELIVERY_SELF_PICKUP.equals(deliveryType)) {
            return selfPickupDays > 0 ? selfPickupDays : expressDefaultDays;
        }
        return expressDefaultDays;
    }

    /** 手机号脱敏：保留前 3 后 4，形如 138****1234（PRD §九 地址/联系方式脱敏）。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String p = phone.trim();
        if (p.length() <= PHONE_HEAD_KEEP + PHONE_TAIL_KEEP) {
            return p.charAt(0) + "****" + p.substring(1);
        }
        return p.substring(0, PHONE_HEAD_KEEP) + "****" + p.substring(p.length() - PHONE_TAIL_KEEP);
    }

    /**
     * 地址脱敏（PRD §九）：隐去门牌号与房间号，保留到楼宇/小区粒度。
     * <p>规则（确定性强、无外部依赖）：
     * <ol>
     *   <li>剥离尾部房间号：{@code 502室 / 502号房 / 502房}；</li>
     *   <li>再剥离尾部门牌号：{@code 100号 / 100弄}；</li>
     *   <li>仍超长（无任何门牌线索的长地址）按 {@link #ADDRESS_MAX_KEEP} 截断并加省略号。</li>
     * </ol>
     * 例：{@code 文一路xx小区3号楼502室} → {@code 文一路xx小区3号楼}；
     * {@code 文一路100号302室} → {@code 文一路}。
     */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        String a = address.trim();
        a = TRAILING_ROOM.matcher(a).replaceAll("");
        a = TRAILING_DOOR.matcher(a).replaceAll("");
        a = a.trim();
        if (a.length() > ADDRESS_MAX_KEEP) {
            return a.substring(0, ADDRESS_MAX_KEEP) + "…";
        }
        return a;
    }

    /** 脱敏后地址最大保留长度（超过则截断加省略号）。 */
    public static final int ADDRESS_MAX_KEEP = 24;

    /** 尾部房间号：502室 / 502号房 / 502房（含全角数字）。 */
    private static final java.util.regex.Pattern TRAILING_ROOM =
            java.util.regex.Pattern.compile("[0-9\\uFF10-\\uFF19]+\\s*(室|号房|房)$");

    /** 尾部门牌号：100号 / 100弄（含全角数字）。 */
    private static final java.util.regex.Pattern TRAILING_DOOR =
            java.util.regex.Pattern.compile("[0-9\\uFF10-\\uFF19]+\\s*(号|弄)$");
}
