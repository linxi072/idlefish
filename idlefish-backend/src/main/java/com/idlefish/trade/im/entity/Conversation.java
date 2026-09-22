package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 会话：买卖家围绕某商品的一对一沟通。
 */
@TableName("t_conversation")
public class Conversation extends BaseEntity implements Serializable {

    /** 会话唯一号（业务维度）。 */
    private String convId;
    private Long buyerId;
    private Long sellerId;
    private Long itemId;
    /** 最近一条消息摘要。 */
    private String lastMessage;
    /** 最近消息发送者（用于前端展示左右气泡）。 */
    private Long lastSenderId;
    /** 买家未读条数。 */
    private Integer buyerUnread = 0;
    /** 卖家未读条数。 */
    private Integer sellerUnread = 0;

    public String getConvId() {
        return convId;
    }

    public void setConvId(String convId) {
        this.convId = convId;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Long getLastSenderId() {
        return lastSenderId;
    }

    public void setLastSenderId(Long lastSenderId) {
        this.lastSenderId = lastSenderId;
    }

    public Integer getBuyerUnread() {
        return buyerUnread;
    }

    public void setBuyerUnread(Integer buyerUnread) {
        this.buyerUnread = buyerUnread;
    }

    public Integer getSellerUnread() {
        return sellerUnread;
    }

    public void setSellerUnread(Integer sellerUnread) {
        this.sellerUnread = sellerUnread;
    }
}
