package com.idlefish.trade.local.dto;

import lombok.Data;

/**
 * 同城配送申请入参（ITER-F05 / REQ-06、REQ-09）。
 */
@Data
public class LocalDeliveryApplyRequest {

    /** 业务类型：ORDER 订单配送 / RECYCLE 回收取件。 */
    private String bizType;

    /** 业务单号（订单号 / 回收单号）。 */
    private String bizNo;

    /** 配送城市（同城可用性判定，REQ-09）。 */
    private String city;

    /** 起点地址（明文入参，服务端脱敏后存储）。 */
    private String fromAddress;

    /** 终点地址（明文入参，服务端脱敏后存储）。 */
    private String toAddress;

    /** 配送距离（公里）；为空时按起步价计费。 */
    private Double distanceKm;

    /** 取件联系人电话（服务端脱敏）。 */
    private String pickupContact;

    /** 收货联系人电话（服务端脱敏）。 */
    private String receiverContact;

    private String remark;
}
