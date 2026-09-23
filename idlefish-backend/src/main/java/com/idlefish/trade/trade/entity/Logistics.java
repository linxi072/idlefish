package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 物流轨迹（D5 物流）：发货后落库物流单与轨迹明细（JSON）。
 */
@TableName("t_logistics")
public class Logistics extends BaseEntity implements Serializable {

    private String orderNo;
    private String logisticsNo;
    private String company;
    /** 状态：transport / signed / exception */
    private String status;
    /** 轨迹明细 JSON（[{time, desc}]） */
    private String detailJson;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getLogisticsNo() {
        return logisticsNo;
    }

    public void setLogisticsNo(String logisticsNo) {
        this.logisticsNo = logisticsNo;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }
}
