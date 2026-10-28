package com.idlefish.trade.web;

import com.idlefish.trade.local.dto.LocalDeliveryApplyRequest;
import com.idlefish.trade.marketing.activity.dto.MarketingActivityCreateDTO;
import com.idlefish.trade.pricing.dto.PriceConfigRequest;
import com.idlefish.trade.pricing.dto.PriceDeviationRequest;
import com.idlefish.trade.pricing.dto.PriceEstimateRequest;
import com.idlefish.trade.pricing.dto.PriceOverrideRequest;
import com.idlefish.trade.search.serving.dto.BehaviorReport;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import com.idlefish.trade.trade.dto.ActivityCreateDTO;
import com.idlefish.trade.trade.dto.ActivityJoinDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 直接对批2 涉及的 10 个纯请求 DTO 跑标准 {@link Validator}，验证字段级约束本身正确：
 * 非法实例应产生约束违例（不被漏校验），合法实例应零违例（不被 400 误杀）。
 * 标准 jakarta Validator，完全离线、不依赖 Spring/MySQL。
 */
class RequestDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private <T> Set<ConstraintViolation<T>> v(T obj) {
        return validator.validate(obj);
    }

    // ===== PriceEstimateRequest：categoryId 必填；conditionLevel 可空（默认3） =====
    @Test
    void priceEstimate_nullCategory_hasViolation() {
        PriceEstimateRequest r = new PriceEstimateRequest();
        r.setConditionLevel(3);
        assertFalse(v(r).isEmpty());
    }

    @Test
    void priceEstimate_valid_noViolation() {
        PriceEstimateRequest r = new PriceEstimateRequest();
        r.setCategoryId(1L);
        r.setConditionLevel(3);
        assertTrue(v(r).isEmpty());
    }

    // ===== PriceDeviationRequest：categoryId/price 必填；price 须为正 =====
    @Test
    void priceDeviation_negativePrice_hasViolation() {
        PriceDeviationRequest r = new PriceDeviationRequest();
        r.setCategoryId(1L);
        r.setConditionLevel(3);
        r.setPrice(-5L);
        assertFalse(v(r).isEmpty());
    }

    @Test
    void priceDeviation_valid_noViolation() {
        PriceDeviationRequest r = new PriceDeviationRequest();
        r.setCategoryId(1L);
        r.setConditionLevel(3);
        r.setPrice(500L);
        assertTrue(v(r).isEmpty());
    }

    // ===== PriceConfigRequest：categoryId/basePriceFen/multiplier 必填且为正；status 可空 =====
    @Test
    void priceConfig_nullBasePrice_hasViolation() {
        PriceConfigRequest r = new PriceConfigRequest();
        r.setCategoryId(1L);
        r.setMultiplier(BigDecimal.valueOf(1.2));
        assertFalse(v(r).isEmpty());
    }

    @Test
    void priceConfig_valid_noViolation() {
        PriceConfigRequest r = new PriceConfigRequest();
        r.setCategoryId(1L);
        r.setBasePriceFen(100L);
        r.setMultiplier(BigDecimal.valueOf(1.2));
        r.setStatus("ENABLED");
        assertTrue(v(r).isEmpty());
    }

    // ===== PriceOverrideRequest：categoryId/multiplier 必填且为正；reason 必填 =====
    @Test
    void priceOverride_blankReason_hasViolation() {
        PriceOverrideRequest r = new PriceOverrideRequest();
        r.setCategoryId(1L);
        r.setMultiplier(BigDecimal.valueOf(1.5));
        r.setReason("   ");
        assertFalse(v(r).isEmpty());
    }

    @Test
    void priceOverride_valid_noViolation() {
        PriceOverrideRequest r = new PriceOverrideRequest();
        r.setCategoryId(1L);
        r.setMultiplier(BigDecimal.valueOf(1.5));
        r.setReason("调价");
        assertTrue(v(r).isEmpty());
    }

    // ===== ActivityCreateDTO：itemId/type/activityPrice(正)/stock(>=0)/startAt/endAt 必填 =====
    @Test
    void activityCreate_nullItemId_hasViolation() {
        ActivityCreateDTO d = new ActivityCreateDTO();
        d.setType("SECKILL");
        d.setActivityPrice(100L);
        d.setStock(10);
        d.setStartAt(LocalDateTime.now());
        d.setEndAt(LocalDateTime.now().plusDays(1));
        assertFalse(v(d).isEmpty());
    }

    @Test
    void activityCreate_negativePrice_hasViolation() {
        ActivityCreateDTO d = new ActivityCreateDTO();
        d.setItemId(1L);
        d.setType("SECKILL");
        d.setActivityPrice(-1L);
        d.setStock(10);
        d.setStartAt(LocalDateTime.now());
        d.setEndAt(LocalDateTime.now().plusDays(1));
        assertFalse(v(d).isEmpty());
    }

    @Test
    void activityCreate_valid_noViolation() {
        ActivityCreateDTO d = new ActivityCreateDTO();
        d.setItemId(1L);
        d.setType("SECKILL");
        d.setActivityPrice(100L);
        d.setStock(10);
        d.setStartAt(LocalDateTime.now());
        d.setEndAt(LocalDateTime.now().plusDays(1));
        assertTrue(v(d).isEmpty());
    }

    // ===== ActivityJoinDTO：activityId/itemId 必填；groupNo/qty 可选 =====
    @Test
    void activityJoin_nullItemId_hasViolation() {
        ActivityJoinDTO d = new ActivityJoinDTO();
        d.setActivityId(1L);
        assertFalse(v(d).isEmpty());
    }

    @Test
    void activityJoin_valid_noViolation() {
        ActivityJoinDTO d = new ActivityJoinDTO();
        d.setActivityId(1L);
        d.setItemId(2L);
        assertTrue(v(d).isEmpty());
    }

    // ===== MarketingActivityCreateDTO：kind/title/itemId/activityPrice(正)/startAt/endAt 必填 =====
    @Test
    void marketingActivity_blankTitle_hasViolation() {
        MarketingActivityCreateDTO d = new MarketingActivityCreateDTO();
        d.setKind("GROUP");
        d.setTitle("");
        d.setItemId(1L);
        d.setActivityPrice(100L);
        d.setStartAt(LocalDateTime.now());
        d.setEndAt(LocalDateTime.now().plusDays(1));
        assertFalse(v(d).isEmpty());
    }

    @Test
    void marketingActivity_valid_noViolation() {
        MarketingActivityCreateDTO d = new MarketingActivityCreateDTO();
        d.setKind("GROUP");
        d.setTitle("双11拼团");
        d.setItemId(1L);
        d.setActivityPrice(100L);
        d.setStartAt(LocalDateTime.now());
        d.setEndAt(LocalDateTime.now().plusDays(1));
        assertTrue(v(d).isEmpty());
    }

    // ===== ItemSyncRequest：itemId/event/occurredAt 必填 =====
    @Test
    void itemSync_blankEvent_hasViolation() {
        ItemSyncRequest r = new ItemSyncRequest(1L, "", 1L);
        assertFalse(v(r).isEmpty());
    }

    @Test
    void itemSync_valid_noViolation() {
        ItemSyncRequest r = new ItemSyncRequest(1L, "UPSERT", 1L);
        assertTrue(v(r).isEmpty());
    }

    // ===== BehaviorReport：userId/event/bizId 必填 =====
    @Test
    void behavior_nullUserId_hasViolation() {
        BehaviorReport r = new BehaviorReport(null, "view", "b1");
        assertFalse(v(r).isEmpty());
    }

    @Test
    void behavior_valid_noViolation() {
        BehaviorReport r = new BehaviorReport(1L, "view", "b1");
        assertTrue(v(r).isEmpty());
    }

    // ===== LocalDeliveryApplyRequest：bizType/bizNo/city/fromAddress/toAddress/pickupContact/receiverContact 必填；distanceKm/remark 可选 =====
    @Test
    void localDelivery_blankBizType_hasViolation() {
        LocalDeliveryApplyRequest r = new LocalDeliveryApplyRequest();
        r.setBizType("");
        r.setBizNo("N1");
        r.setCity("上海");
        r.setFromAddress("A");
        r.setToAddress("B");
        r.setPickupContact("p");
        r.setReceiverContact("r");
        assertFalse(v(r).isEmpty());
    }

    @Test
    void localDelivery_valid_noViolation() {
        LocalDeliveryApplyRequest r = new LocalDeliveryApplyRequest();
        r.setBizType("ORDER");
        r.setBizNo("N1");
        r.setCity("上海");
        r.setFromAddress("A");
        r.setToAddress("B");
        r.setPickupContact("p");
        r.setReceiverContact("r");
        assertTrue(v(r).isEmpty());
    }
}
