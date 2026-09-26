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

    /** 积分抵现：下单使用的积分（F-13.1），选填；>0 则下单时核验并抵扣。 */
    private Long usedPoint;

    /** 参与活动 ID（F-13.3 拼团/秒杀），选填；非空则下单时锁定活动价与活动库存。 */
    private Long activityId;

    /** 加入的拼团号（F-13.3 GROUP 类型团长 ID 字符串），选填；为空则自建团。 */
    private String groupNo;

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

    public Long getUsedPoint() {
        return usedPoint;
    }

    public void setUsedPoint(Long usedPoint) {
        this.usedPoint = usedPoint;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public String getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }
}
