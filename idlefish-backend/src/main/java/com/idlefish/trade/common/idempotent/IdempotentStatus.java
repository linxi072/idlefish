package com.idlefish.trade.common.idempotent;

/**
 * 幂等记录三态。
 */
public enum IdempotentStatus {

    /** 执行中：首次请求已占用，重复请求应拒绝（超时后可被接管）。 */
    PROCESSING,

    /** 已完成：重复请求回放首次结果，不再执行业务。 */
    SUCCESS,

    /** 已失败：允许删除后重新执行（保证用户可重试）。 */
    FAILED
}
