package com.idlefish.trade.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 下单入参。
 */
public class OrderCreateDTO {

    @NotNull(message = "商品ID不能为空")
    private Long itemId;

    @Min(1)
    private Integer quantity = 1;

    @NotNull(message = "请选择收货地址")
    private Long addressId;

    private String remark;

    /** 优惠券：用户券 ID（t_user_coupon.id），选填；非空则下单时核销抵扣。 */
    private Long userCouponId;

    /** 幂等键（客户端生成，防止重复提交；当前仅透传，后端以"同买家同商品待支付"幂等）。 */
    private String idempotentKey;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }
}
