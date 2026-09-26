package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 拼团实例（F-13.3，GROUP 类型）：一次成团记录。
 * 团长首参创建，其他人参团加入；人数达标转 SUCCESS，超时未达标转 FAILED 并释放库存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_activity_group")
public class ActivityGroup extends BaseEntity {

    private Long activityId;
    private Long leaderId;        // 团长
    private Integer currentSize;  // 当前参团人数
    private Integer targetSize;   // 成团目标人数
    private String status;        // OPEN / SUCCESS / FAILED
    private LocalDateTime expireAt; // 成团截止时间
}
