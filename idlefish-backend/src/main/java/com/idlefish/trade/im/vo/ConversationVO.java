package com.idlefish.trade.im.vo;

import java.util.List;

/**
 * 会话视图：含对方信息、最近消息、未读数。
 */
public class ConversationVO {

    private String convId;
    private Long peerId;
    private String peerName;
    private Long itemId;
    private String lastMessage;
    private Long lastSenderId;
    private Integer unread;
    private String lastTime;
    private List<MessageVO> recent;

    public String getConvId() {
        return convId;
    }

    public void setConvId(String convId) {
        this.convId = convId;
    }

    public Long getPeerId() {
        return peerId;
    }

    public void setPeerId(Long peerId) {
        this.peerId = peerId;
    }

    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(String peerName) {
        this.peerName = peerName;
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

    public Integer getUnread() {
        return unread;
    }

    public void setUnread(Integer unread) {
        this.unread = unread;
    }

    public String getLastTime() {
        return lastTime;
    }

    public void setLastTime(String lastTime) {
        this.lastTime = lastTime;
    }

    public List<MessageVO> getRecent() {
        return recent;
    }

    public void setRecent(List<MessageVO> recent) {
        this.recent = recent;
    }
}
