package com.idlefish.trade.common;

import com.idlefish.trade.common.Code;
import lombok.Data;

import java.util.UUID;

/**
 * 统一响应体（PRD §十.1）。
 * { "code": 0, "msg": "success", "data": {}, "trace_id": "...", "ts": ... }
 */
@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;
    private String traceId;
    private long ts;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.ts = System.currentTimeMillis();
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(Code.SUCCESS.getCode(), Code.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(Code code) {
        return new Result<>(code.getCode(), code.getMsg(), null);
    }
}
