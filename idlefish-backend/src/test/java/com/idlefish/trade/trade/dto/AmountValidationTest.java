package com.idlefish.trade.trade.dto;

import com.idlefish.trade.marketing.dto.CouponCreateDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金额「分」单位校验降级方案验证（#7）：通过 jakarta.validation 工厂直接校验 DTO 注解，
 * 不依赖 Spring 容器 / MySQL，纯离线可跑。验证核心资金入参的负值 / 越界被拦截。
 */
public class AmountValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.close();
    }

    @Test
    void refundAmount_negative_shouldBeRejected() {
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderNo("T20260901001");
        dto.setType("only_refund");
        dto.setAmount(-1L); // 负值（分）应被 @Min(0) 拦截
        Set<ConstraintViolation<RefundApplyDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "负金额应触发约束违规");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("amount")));
    }

    @Test
    void refundAmount_zero_shouldPass() {
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderNo("T20260901002");
        dto.setType("only_refund");
        dto.setAmount(0L);
        Set<ConstraintViolation<RefundApplyDTO>> violations = validator.validate(dto);
        // 0 满足 @Min(0)，仅校验金额字段本身不应因 amount 报错（其余必填项可能缺）
        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("amount")));
    }

    @Test
    void couponReduceAmount_negative_shouldBeRejected() {
        CouponCreateDTO dto = new CouponCreateDTO();
        dto.setName("测试券");
        dto.setType("FULL_REDUCTION");
        dto.setReduceAmount(-5L); // 负值（分）应被 @Min(0) 拦截
        Set<ConstraintViolation<CouponCreateDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("reduceAmount")));
    }

    @Test
    void couponDiscountRate_overOne_shouldBeRejected() {
        CouponCreateDTO dto = new CouponCreateDTO();
        dto.setName("折扣券");
        dto.setType("DISCOUNT");
        dto.setDiscountRate(2.0); // 超过 1.0 应被 @DecimalMax(1.0) 拦截
        Set<ConstraintViolation<CouponCreateDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discountRate")));
    }

    @Test
    void couponAmount_valid_shouldPass() {
        CouponCreateDTO dto = new CouponCreateDTO();
        dto.setName("有效券");
        dto.setType("FULL_REDUCTION");
        dto.setThresholdAmount(1000L);
        dto.setReduceAmount(200L);
        dto.setMaxDiscountAmount(0L);
        dto.setDiscountRate(0.9);
        Set<ConstraintViolation<CouponCreateDTO>> violations = validator.validate(dto);
        assertEquals(0, violations.stream()
                .filter(v -> v.getPropertyPath().toString().matches("thresholdAmount|reduceAmount|maxDiscountAmount|discountRate"))
                .count());
    }
}
