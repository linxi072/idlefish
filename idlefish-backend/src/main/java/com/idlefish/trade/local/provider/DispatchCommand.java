package com.idlefish.trade.local.provider;

import lombok.Data;

/**
 * 第三方同城调度的下单入参（ITER-F05 / REQ-06）。
 * 金额恒「分」、距离恒「公里」。
 */
@Data
public class DispatchCommand {

    /** 我方同城配送单号（LD 前缀）。 */
    private String deliveryNo;

    /** 业务类型：ORDER 订单配送 / RECYCLE 回收取件。 */
    private String bizType;

    /** 业务单号（订单号 / 回收单号）。 */
    private String bizNo;

    /** 配送城市。 */
    private String city;

    /** 起点（已脱敏）地址。 */
    private String fromAddress;

    /** 终点（已脱敏）地址。 */
    private String toAddress;

    /** 取件联系人（脱敏后电话）。 */
    private String pickupContact;

    /** 送达联系人（脱敏后电话）。 */
    private String receiverContact;

    /** 配送距离（公里）。 */
    private Double distanceKm;

    /** 备注（如「易碎品轻拿轻放」）。 */
    private String remark;
}
