package com.idlefish.trade.trade.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 发起议价入参（PRD §D3）。金额单位：分。
 */
public class BargainCreateDTO implements Serializable {

    @NotNull(message = "商品ID必填")
    private Long itemId;

    @NotNull(message = "卖家ID必填")
    private Long sellerId;

    @NotNull(message = "会话ID必填")
    private String convId;

    @NotNull(message = "出价必填")
    private Long offerPrice;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public String getConvId() {
        return convId;
    }

    public void setConvId(String convId) {
        this.convId = convId;
    }

    public Long getOfferPrice() {
        return offerPrice;
    }

    public void setOfferPrice(Long offerPrice) {
        this.offerPrice = offerPrice;
    }
}
