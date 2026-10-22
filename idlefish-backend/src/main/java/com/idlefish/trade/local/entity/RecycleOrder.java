package com.idlefish.trade.local.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 上门回收单（ITER-F05 同城回收与本地履约）。
 * <p>
 * 金额单位一律「分」（{@code estimateAmount} 预估价 / {@code finalAmount} 质检后最终价），
 * 放款统一走既有 {@code FundFlowBuilder} 入账，并与 ITER-T02 多维对账口径对齐（REQ-07）。
 * 状态流转见 {@link com.idlefish.trade.local.RecycleStateMachine}。
 *
 * <p>合规要求（PRD §九）：联系方式与门牌号在落库前须脱敏，服务端只保留脱敏后的
 * {@code address} / {@code contactPhone}，禁止存储明文完整地址。
 * 数据隔离：{@code orgId} 为区域机构（REQ-08），后台与查询均按机构范围收敛。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recycle_order")
public class RecycleOrder extends BaseEntity {

    /** 回收单号（唯一，RC 前缀，见 IdGenerator.recycleNo）。 */
    private String recycleNo;

    /** 申请人（卖家）userId。 */
    private Long userId;

    /** 所属区域机构 ID（REQ-08 数据隔离；为空表示未归属，仅超管可见）。 */
    private Long orgId;

    /** 关联商品 ID（一键回收场景 REQ-11 必填；独立回收申请可为空）。 */
    private Long itemId;

    /** 商品类目 ID（回收白名单校验 REQ-11 与估价分位均以此为维度）。 */
    private Long categoryId;

    /** 回收品标题快照（商品下架后仍可追溯；等价于 Item 标题冗余）。 */
    private String title;

    /** 成色档位：1 全新 / 2 95新 / 3 9成新 / 4 8成新 / 5 功能完好。 */
    private Integer conditionLevel;

    /** 取件方式：HOME 上门取件 / SELF 用户自送到店。 */
    private String pickupType;

    /** 系统预估回收价（分），由 RecycleQuoteCalculator 依据参考价与成色系数得出（REQ-02）。 */
    private Long estimateAmount;

    /** 质检后最终回收价（分），待用户确认（REQ-04）。 */
    private Long finalAmount;

    /** 状态：见 RecycleStateMachine。 */
    private String status;

    /** 省 / 市 / 区（城市用于同城可用性判定 REQ-01/REQ-09）。 */
    private String province;
    private String city;
    private String district;

    /** 脱敏后的取件地址（隐去门牌号，仅保留到小区/楼宇粒度）。 */
    private String address;

    /** 联系人姓名。 */
    private String contactName;

    /** 脱敏后的联系电话（如 138****1234）。 */
    private String contactPhone;

    /** 预约上门时间。 */
    private LocalDateTime appointmentAt;

    /** 质检调价说明（为何从预估价调整到最终价，REQ-04 须对用户明示）。 */
    private String quoteRemark;

    /** 第三方回收商/服务商返回的外部单号（对接留痕）。 */
    private String serialNo;

    /** 取消原因（待上门阶段取消不收取任何费用 REQ-03）。 */
    private String cancelReason;

    /** 运营处理人标识（后台质检/放款操作人）。 */
    private String operator;

    /** 放款时间（REQ-07 回收款入账即异步线程写资金流水）。 */
    private LocalDateTime paidAt;

    /** 取件（揽收）时间。 */
    private LocalDateTime pickedUpAt;

    /** 质检完成时间。 */
    private LocalDateTime quotedAt;

    /** 终态时间（放款/取消/拒绝/不可用）。 */
    private LocalDateTime finishedAt;
}
