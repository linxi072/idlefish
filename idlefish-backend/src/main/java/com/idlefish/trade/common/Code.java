package com.idlefish.trade.common;

import lombok.Getter;

/**
 * 错误码规范（PRD §十.2 区间划分）。
 * 1xxxx 参数错误 / 2xxxx 认证授权 / 3xxxx 业务规则 / 4xxxx 资源不存在 / 5xxxx 系统或依赖。
 */
@Getter
public enum Code {

    SUCCESS(0, "success"),

    // 1xxxx 参数错误
    PARAM_MISSING(10001, "必填参数缺失"),
    PARAM_INVALID(10002, "参数格式错误"),

    // 2xxxx 认证授权
    UNAUTHORIZED(20001, "未登录或登录失效"),
    TOKEN_EXPIRED(20002, "token 已过期"),
    FORBIDDEN(20003, "无权限访问"),

    // 3xxxx 业务规则
    STOCK_NOT_ENOUGH(30001, "库存不足"),
    STATE_NOT_ALLOWED(30002, "当前状态不允许该操作"),
    BARGAIN_EXPIRED(30003, "议价已失效"),
    FREQUENCY_LIMIT(30004, "操作过于频繁，请稍后再试"),
    BALANCE_NOT_ENOUGH(30005, "账户余额不足"),
    BIZ_ERROR(30006, "业务校验失败"),
    COUPON_NOT_FOUND(30007, "优惠券不存在"),
    COUPON_INVALID(30008, "优惠券不可用"),
    COUPON_SOLD_OUT(30009, "优惠券已抢光"),
    POINT_NOT_ENOUGH(30010, "积分不足"),
    ACTIVITY_NOT_FOUND(30011, "活动不存在"),
    ACTIVITY_NOT_ONGOING(30012, "活动未开始或已结束"),
    ACTIVITY_JOIN_LIMIT(30013, "活动参与次数已达上限"),
    GROUP_FULL(30014, "该拼团已满"),
    GROUP_NOT_FOUND(30015, "拼团不存在或已结束"),
    INVITE_CODE_NOT_FOUND(30016, "邀请码无效"),
    INVITE_SELF(30017, "不能填写自己的邀请码"),
    INVITE_ALREADY_BOUND(30018, "您已绑定邀请关系"),
    DISPUTE_NOT_FOUND(30019, "维权工单不存在"),
    DISPUTE_STATE_NOT_ALLOWED(30020, "当前工单状态不允许该操作"),
    DISPUTE_EXISTS(30021, "该订单存在未结维权工单"),
    INSPECTION_NOT_FOUND(30023, "验货单不存在"),
    INSPECTION_STATE_NOT_ALLOWED(30024, "当前验货单状态不允许该操作"),
    INSPECTION_EXISTS(30025, "该订单已存在进行中的验货单"),
    /** 估价配置不存在（ITER-F03：运营系数覆盖需先配置基准价）。 */
    PRICING_CONFIG_NOT_FOUND(30026, "估价配置不存在"),
    /** 幂等：同一请求正在处理中（重复提交被拦截，F-18 幂等框架）。 */
    BIZ_PROCESSING(30022, "处理中，请勿重复提交"),
    /** 真机验收闭环（ITER-F01）：验收用例不存在。 */
    ACCEPTANCE_NOT_FOUND(30027, "验收用例不存在"),
    /** 真机验收闭环（ITER-F01）：验收未闭环（存在未通过用例或未关闭缺陷）禁止关单。 */
    ACCEPTANCE_NOT_CLOSED(30028, "验收未闭环，存在未通过用例或未关闭缺陷"),
    /** AB 实验不存在（ITER-F04 搜索推荐深化 / REQ-06 稳定分桶与回滚）。 */
    AB_EXPERIMENT_NOT_FOUND(30029, "实验不存在"),
    /** AB 实验配置冲突：名称重复或时间窗重叠（ITER-F04 / REQ-06）。 */
    AB_EXPERIMENT_CONFLICT(30030, "实验配置冲突（名称或时间窗重叠）"),
    /** 同城回收（ITER-F05 / REQ-02）：回收单不存在。 */
    RECYCLE_NOT_FOUND(30031, "回收单不存在"),
    /** 同城回收（ITER-F05）：当前回收单状态不允许该操作（如已放款不可取消）。 */
    RECYCLE_STATE_NOT_ALLOWED(30032, "当前回收单状态不允许该操作"),
    /** 同城回收（ITER-F05 / REQ-05）：用户已拒绝质检报价，不得强制成交或扣费。 */
    RECYCLE_QUOTE_REJECTED(30033, "用户拒绝质检报价，回收单已取消并退回"),
    /** 同城回收（ITER-F05 / REQ-11）：品类不在回收白名单，不支持一键回收。 */
    RECYCLE_CATEGORY_NOT_ALLOWED(30034, "该品类暂不支持回收"),
    /** 同城配送（ITER-F05 / REQ-06）：配送单不存在。 */
    LOCAL_DELIVERY_NOT_FOUND(30035, "同城配送单不存在"),
    /** 同城配送（ITER-F05 / REQ-09）：第三方服务商不可用，须明确提示而非静默失败。 */
    LOCAL_DELIVERY_UNAVAILABLE(30036, "同城配送服务暂不可用，请稍后重试或改用快递"),
    /** 同城配送（ITER-F05）：当前配送单状态不允许该操作。 */
    LOCAL_DELIVERY_STATE_NOT_ALLOWED(30037, "当前配送单状态不允许该操作"),
    /** 同城履约（ITER-F05 / REQ-09）：城市未开通同城服务（回收或配送）。 */
    LOCAL_REGION_UNSUPPORTED(30038, "所在城市暂未开通同城服务"),
    /** 数据范围越权（ITER-T05 / REQ-03）：访问超出自身数据范围的机构数据被拒绝。 */
    DATA_SCOPE_FORBIDDEN(30039, "无数据访问权限（超出数据范围）"),
    /** 机构停用（ITER-T05 / REQ-06）：所属机构已停用，禁止登录。 */
    ORG_DISABLED(30040, "所属机构已停用，禁止登录"),
    /** 导出类型或格式不支持（ITER-T06 运营数据导出中心 / REQ-07）。 */
    EXPORT_TYPE_UNSUPPORTED(30041, "不支持的导出类型或格式"),
    /** 单次导出行数 / 文件体积超过上限（ITER-T06）。 */
    EXPORT_LIMIT_EXCEEDED(30042, "单次导出超过上限"),
    /** 导出数据范围越权（ITER-T06 / REQ-07）：无 orgId 实体仅 ALL 可导出 / 跨机构越权请求。 */
    EXPORT_DATA_SCOPE_FORBIDDEN(30043, "无数据权限的导出请求（超出可见范围或无 orgId 实体仅限超管导出）"),
    /** 导出任务不存在（ITER-T06）。 */
    EXPORT_TASK_NOT_FOUND(30044, "导出任务不存在"),
    /** 导出文件不存在或已清理（ITER-T06）。 */
    EXPORT_FILE_NOT_FOUND(30045, "导出文件不存在或已清理"),
    /** 事件类型不支持 / 未注册（ITER-T06 事件驱动架构 / REQ-02 schema 版本化）。 */
    EVENT_TYPE_UNSUPPORTED(30046, "不支持的事件类型"),
    /** 事件不存在（按事件 ID / 回放单号查询未命中，ITER-T06）。 */
    EVENT_NOT_FOUND(30047, "事件不存在"),
    /** 事件回放任务不存在（ITER-T06 / REQ-13）。 */
    EVENT_REPLAY_NOT_FOUND(30048, "回放任务不存在"),
    /** 回放任务创建超限（时间窗 / 事件量超过上限，ITER-T06 / REQ-13）。 */
    EVENT_REPLAY_LIMIT(30049, "回放任务超过上限（时间窗过大或事件量超限）"),
    /** 无可重投死信（ITER-T06 / REQ-08）。 */
    EVENT_DEAD_LETTER_EMPTY(30050, "无可重投的死信事件"),

    // 4xxxx 资源不存在
    USER_NOT_FOUND(40001, "用户不存在"),
    ITEM_NOT_FOUND(40002, "商品不存在"),
    ORDER_NOT_FOUND(40003, "订单不存在"),
    CONV_NOT_FOUND(40004, "会话不存在"),
    CATEGORY_NOT_FOUND(40005, "类目不存在"),
    NOT_FOUND(40400, "资源不存在"),

    // 5xxxx 系统 / 依赖
    PAY_CHANNEL_ERROR(50001, "支付渠道异常"),
    SEARCH_ERROR(50002, "搜索服务异常"),
    MQ_ERROR(50003, "消息队列异常"),
    FILE_ERROR(50004, "文件上传异常"),
    WX_LOGIN_ERROR(50005, "微信登录校验失败"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试"),
    FUND_TRANSFER_FAILED(50006, "提现出款失败"),
    CONFIG_MISSING(50007, "外部依赖配置缺失，操作未执行");

    private final int code;
    private final String msg;

    Code(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
