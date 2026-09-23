package com.idlefish.trade.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品状态流转日志（PRD §B4）：每次状态迁移留痕，便于审计与排查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_item_status_log")
public class ItemStatusLog extends BaseEntity {

    private Long itemId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;  // 操作人（用户/系统/管理员）
    private String remark;
}
