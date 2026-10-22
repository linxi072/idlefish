package com.idlefish.trade.local.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同城配送单（ITER-F05 本地履约 / REQ-06）。
 * <p>
 * 统一挂载两类业务：{@code bizType=ORDER} 订单同城配送、{@code bizType=RECYCLE} 回收上门取件。
 * 不自建车队：调度能力由第三方服务商 {@code LocalFulfillmentProvider} 承接，
 * 服务商不可用或超范围时明确返回 LOCAL_DELIVERY_UNAVAILABLE（REQ-09 禁止静默失败）。
 *
 * <p>轨迹闭环：第三方回传的轨迹节点经 {@code LogisticsTimelineBuilder} 归一化后写入既有
 * {@code t_logistics}（复用 {@code LogisticService.track} 口径），前端沿用物流时间轴渲染，
 * 不新增轨迹表。金额单位一律「分」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_local_delivery")
public class LocalDelivery extends BaseEntity {

    /** 同城配送单号（唯一，LD 前缀，见 IdGenerator.deliveryNo）。 */
    private String deliveryNo;

    /** 业务类型：ORDER 订单配送 / RECYCLE 回收取件。 */
    private String bizType;

    /** 业务单号（订单号 或 回收单号）。 */
    private String bizNo;

    /** 发起方 userId（订单场景为买家，回收场景为回收申请人）。 */
    private Long userId;

    /** 配送城市（同城可用性判定 REQ-01/REQ-09）。 */
    private String city;

    /** 起点（脱敏）地址。 */
    private String fromAddress;

    /** 终点（脱敏）地址。 */
    private String toAddress;

    /** 配送距离（公里），用于运费计费与超范围拒绝（REQ-09）。 */
    private Double distanceKm;

    /** 配送费（分），由 LocalFulfillmentRules 按起步价 + 里程费算出（REQ-07）。 */
    private Long freight;

    /** 配送费是否已入账（配合资金流水，避免重复入账）。 */
    private Integer paid;

    /** 状态：见 LocalDeliveryStateMachine。 */
    private String status;

    /** 第三方服务商名称（配置 idlefish.local.provider.name）。 */
    private String providerName;

    /** 第三方返回的运单号（对接留痕与轨迹查询入参）。 */
    private String providerNo;

    /** 骑手姓名。 */
    private String courierName;

    /** 脱敏后的骑手联系电话（如 138****1234）。 */
    private String courierPhone;

    /** 失败/不可用原因（REQ-09 须对用户明示）。 */
    private String failReason;

    /** 骑手接单时间。 */
    private LocalDateTime acceptedAt;

    /** 送达时间。 */
    private LocalDateTime deliveredAt;

    /** 终态时间（签收/取消/失败）。 */
    private LocalDateTime finishedAt;
}
