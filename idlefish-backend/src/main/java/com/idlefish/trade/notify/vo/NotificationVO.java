package com.idlefish.trade.notify.vo;

import lombok.Data;

/**
 * 通知卡片 VO（前端展示用）。
 */
@Data
public class NotificationVO {

    private Long id;
    private String type;            // 通知类型 code
    private String bizId;
    private String title;
    private String content;
    private Integer read;           // 0 未读 / 1 已读
    private String createdAt;
}
