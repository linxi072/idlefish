package com.idlefish.trade.trade.dto;

import java.io.Serializable;

/**
 * 参与活动入参（F-13.3）：拼团可带 groupNo 加入已有团，否则自建团。
 */
public class ActivityJoinDTO implements Serializable {

    private Long activityId;
    private Long itemId;
    private String groupNo;       // 加入已有拼团时传（GROUP 类型团长 ID 字符串）
    private Integer qty = 1;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
