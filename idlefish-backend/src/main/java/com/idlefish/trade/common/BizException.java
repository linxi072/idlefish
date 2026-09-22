package com.idlefish.trade.common;

import lombok.Getter;

/**
 * 业务异常：携带错误码，由 GlobalExceptionHandler 统一转换为 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(Code code) {
        super(code.getMsg());
        this.code = code.getCode();
    }

    public BizException(Code code, String detail) {
        super(detail);
        this.code = code.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
