package com.idlefish.trade.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 消息：会话内单条消息（文本 / 图片）。
 */
@TableName("t_message")
public class Message extends BaseEntity implements Serializable {

    private String convId;
    private Long senderId;
    private Long receiverId;
    /** text / image。 */
    private String type = "text";
    private String content;
    /** 会话内自增序号（断线补拉、排序、已读回执锚点，PRD §E1）。 */
    private Long seq;
    /** 0 未读 / 1 已读。 */
    private Integer readFlag = 0;

    public String getConvId() {
        return convId;
    }

    public void setConvId(String convId) {
        this.convId = convId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Integer getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Integer readFlag) {
        this.readFlag = readFlag;
    }
}
