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
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String msg;

    Code(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
