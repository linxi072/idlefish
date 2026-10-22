package com.idlefish.trade.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同城履约规则纯函数单测（ITER-F05）：城市/品类可用性、运费计费、确认时效、PII 脱敏。
 */
class LocalFulfillmentRulesTest {

    // ===== 履约方式 =====

    @Test
    @DisplayName("三种履约方式合法，其余非法")
    void deliveryTypeValidation() {
        assertTrue(LocalFulfillmentRules.validDeliveryType(LocalFulfillmentRules.DELIVERY_EXPRESS));
        assertTrue(LocalFulfillmentRules.validDeliveryType(LocalFulfillmentRules.DELIVERY_SELF_PICKUP));
        assertTrue(LocalFulfillmentRules.validDeliveryType(LocalFulfillmentRules.DELIVERY_LOCAL));
        assertFalse(LocalFulfillmentRules.validDeliveryType("TELEPORT"));
        assertFalse(LocalFulfillmentRules.validDeliveryType(null));
    }

    @Test
    @DisplayName("自提与同城配送都算同城履约")
    void isLocal() {
        assertTrue(LocalFulfillmentRules.isLocal(LocalFulfillmentRules.DELIVERY_SELF_PICKUP));
        assertTrue(LocalFulfillmentRules.isLocal(LocalFulfillmentRules.DELIVERY_LOCAL));
        assertFalse(LocalFulfillmentRules.isLocal(LocalFulfillmentRules.DELIVERY_EXPRESS));
        assertFalse(LocalFulfillmentRules.isLocal(null));
    }

    // ===== 城市可用性（REQ-01/09） =====

    @Test
    @DisplayName("城市为空一律不可用（不可对无城市信息下单）")
    void cityBlankNotSupported() {
        assertFalse(LocalFulfillmentRules.isCitySupported(null, List.of("杭州")));
        assertFalse(LocalFulfillmentRules.isCitySupported("   ", List.of("杭州")));
        assertFalse(LocalFulfillmentRules.isCitySupported(null, Collections.emptyList()));
    }

    @Test
    @DisplayName("白名单为空表示不限制")
    void emptyWhitelistMeansUnrestricted() {
        assertTrue(LocalFulfillmentRules.isCitySupported("北京", Collections.emptyList()));
        assertTrue(LocalFulfillmentRules.isCitySupported("北京", null));
        // 仅有空白项的名单同样视为不限制，避免配置噪声误伤全部城市
        assertTrue(LocalFulfillmentRules.isCitySupported("北京", Arrays.asList("", null)));
    }

    @Test
    @DisplayName("命中白名单：精确匹配与市/区前缀均视为命中")
    void cityWhitelistMatching() {
        List<String> whitelist = List.of("杭州", "上海");
        assertTrue(LocalFulfillmentRules.isCitySupported("杭州", whitelist));
        assertTrue(LocalFulfillmentRules.isCitySupported("杭州市", whitelist));
        assertFalse(LocalFulfillmentRules.isCitySupported("北京", whitelist));
    }

    // ===== 品类白名单（REQ-11） =====

    @Test
    @DisplayName("品类白名单：空名单不限制，命中放行，未命中拒绝")
    void categoryWhitelist() {
        assertFalse(LocalFulfillmentRules.isCategoryRecyclable(null, List.of(1L)));
        assertTrue(LocalFulfillmentRules.isCategoryRecyclable(9L, Collections.emptyList()));
        assertTrue(LocalFulfillmentRules.isCategoryRecyclable(9L, null));
        assertTrue(LocalFulfillmentRules.isCategoryRecyclable(9L, Arrays.asList(1L, null, 9L)));
        assertFalse(LocalFulfillmentRules.isCategoryRecyclable(3L, List.of(1L, 2L)));
    }

    // ===== 距离与运费（REQ-07） =====

    @Test
    @DisplayName("超范围判定：距离为空 / 超上限均不可用")
    void distanceSupported() {
        assertFalse(LocalFulfillmentRules.isDistanceSupported(null, 30d));
        assertTrue(LocalFulfillmentRules.isDistanceSupported(0d, 30d));
        assertTrue(LocalFulfillmentRules.isDistanceSupported(30d, 30d));
        assertFalse(LocalFulfillmentRules.isDistanceSupported(30.01d, 30d));
    }

    @Test
    @DisplayName("运费：起步里程内只收起步价，超出按公里向上取整累加")
    void freightCalculation() {
        assertEquals(600L, LocalFulfillmentRules.calcFreight(2.0, 600L, 3.0, 200L));
        assertEquals(600L, LocalFulfillmentRules.calcFreight(3.0, 600L, 3.0, 200L));
        // 3.1 公里：超出 0.1 公里按 1 公里计
        assertEquals(800L, LocalFulfillmentRules.calcFreight(3.1, 600L, 3.0, 200L));
        // 5 公里：超出 2 公里
        assertEquals(1000L, LocalFulfillmentRules.calcFreight(5.0, 600L, 3.0, 200L));
    }

    // ===== 确认时效（REQ-10） =====

    @Test
    @DisplayName("REQ-10 自提自动确认天数显著短于快递；配置非法时回落快递默认")
    void confirmDays() {
        assertEquals(3, LocalFulfillmentRules.confirmDays(LocalFulfillmentRules.DELIVERY_SELF_PICKUP, 3, 10));
        assertEquals(10, LocalFulfillmentRules.confirmDays(LocalFulfillmentRules.DELIVERY_EXPRESS, 3, 10));
        assertEquals(10, LocalFulfillmentRules.confirmDays(LocalFulfillmentRules.DELIVERY_LOCAL, 3, 10));
        assertEquals(10, LocalFulfillmentRules.confirmDays(LocalFulfillmentRules.DELIVERY_SELF_PICKUP, 0, 10));
        assertEquals(10, LocalFulfillmentRules.confirmDays(LocalFulfillmentRules.DELIVERY_SELF_PICKUP, -1, 10));
    }

    // ===== PII 脱敏（PRD §九） =====

    @Test
    @DisplayName("手机号脱敏保留前 3 后 4")
    void maskPhone() {
        assertEquals("138****1234", LocalFulfillmentRules.maskPhone("13812341234"));
        assertEquals("138****1234", LocalFulfillmentRules.maskPhone(" 13812341234 "));
        // 带国家码与分隔符时按原串截断，不解析归属（保持确定性）
        assertEquals("+86****1234", LocalFulfillmentRules.maskPhone("+86-138-1234-1234"));
    }

    @Test
    @DisplayName("手机号过短时仍脱敏，不暴露完整号码")
    void maskPhone_short() {
        assertEquals("1****2", LocalFulfillmentRules.maskPhone("12"));
        assertEquals("139****9999", LocalFulfillmentRules.maskPhone("13999999999"));
    }

    @Test
    @DisplayName("地址脱敏：保留到楼栋/小区粒度，隐去门牌号与房间号")
    void maskAddress() {
        // 房间号剥离，楼栋保留
        assertEquals("浙江省杭州市西湖区文一路xx小区3号楼",
                LocalFulfillmentRules.maskAddress("浙江省杭州市西湖区文一路xx小区3号楼502室"));
        // 无房间号但有门牌号：剥离门牌号
        assertEquals("文一路", LocalFulfillmentRules.maskAddress("文一路100号302室"));
        // 无门牌线索：原样保留
        assertEquals("上海市浦东新区张江镇", LocalFulfillmentRules.maskAddress("上海市浦东新区张江镇"));
        // 无门牌线索且过长：按 24 字截断
        assertEquals("这是一段没有任何门牌房间线索的很长的地址描述文本…",
                LocalFulfillmentRules.maskAddress("这是一段没有任何门牌房间线索的很长的地址描述文本需要被截断处理补充内容"));
    }
}
