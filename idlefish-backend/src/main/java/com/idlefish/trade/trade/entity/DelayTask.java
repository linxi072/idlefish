package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 延时任务（D6 延时队列本地实现）：持久化到期需执行的任务，由定时扫描触发，确保不丢。
 */
@TableName("t_delay_task")
public class DelayTask extends BaseEntity implements Serializable {

    private String taskType;
    private String bizId;
    private String payload;
    private String status;
    private LocalDateTime nextExecuteAt;
    private Integer retryCount;

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getNextExecuteAt() {
        return nextExecuteAt;
    }

    public void setNextExecuteAt(LocalDateTime nextExecuteAt) {
        this.nextExecuteAt = nextExecuteAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}
