package com.idlefish.trade.search.serving;

/**
 * 搜推服务化内部异常（ITER-T04 / REQ-03/08）：端口调用失败（远程超时/熔断拒绝/内联依赖异常）时抛出，
 * 由 {@link SearchServingFacade} 捕获后走降级阶梯，不向上泄漏。
 */
public class SearchServingException extends RuntimeException {

    private final String reason;

    public SearchServingException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SearchServingException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** 降级归因（remote_timeout / remote_error / breaker_open / inline_error / executor_unavailable）。 */
    public String getReason() {
        return reason;
    }
}
