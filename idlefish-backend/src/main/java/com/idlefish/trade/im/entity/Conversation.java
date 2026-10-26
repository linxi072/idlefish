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
    /**
     * 智能助手自动代答开关（ITER-F06）：卖家可在会话级关闭（0=关闭，1=开启，默认 1）。
     * 关闭后买家消息不再触发自动应答，仅转人工链路。
     */
    private Integer autoReplyEnabled = 1;
    /**
     * 人工接管标记（ITER-F06 / REQ-09）：买家要求转人工或裁决类升级后置 1，
     * 之后停止一切自动作答，直至卖家/客服手动恢复。
     */
    private Integer humanTakeover = 0;
    /** ITER-F06 会话累计纠纷风险分（窗口内，封顶 100；超过 disputeSignalWindowHours 未更新则重置）。 */
    private Integer disputeRiskScore = 0;
    /** ITER-F06 风险分最近更新时间（窗口重置基准）。 */
    private java.time.LocalDateTime disputeRiskAt;

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

    public Integer getAutoReplyEnabled() {
        return autoReplyEnabled;
    }

    public void setAutoReplyEnabled(Integer autoReplyEnabled) {
        this.autoReplyEnabled = autoReplyEnabled;
    }

    public Integer getHumanTakeover() {
        return humanTakeover;
    }

    public void setHumanTakeover(Integer humanTakeover) {
        this.humanTakeover = humanTakeover;
    }

    public Integer getDisputeRiskScore() {
        return disputeRiskScore;
    }

    public void setDisputeRiskScore(Integer disputeRiskScore) {
        this.disputeRiskScore = disputeRiskScore;
    }

    public java.time.LocalDateTime getDisputeRiskAt() {
        return disputeRiskAt;
    }

    public void setDisputeRiskAt(java.time.LocalDateTime disputeRiskAt) {
        this.disputeRiskAt = disputeRiskAt;
    }
}
