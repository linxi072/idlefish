package com.idlefish.trade.datascope;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;

/**
 * 数据权限越权异常（继承 {@link BizException}，由全局异常处理器统一转为 {@link Result}）。
 * 对应 {@link Code#DATA_SCOPE_FORBIDDEN}（REQ-03 越权拒绝）。
 */
public class DataScopeException extends BizException {

    public DataScopeException() {
        super(Code.DATA_SCOPE_FORBIDDEN);
    }

    public DataScopeException(String detail) {
        super(Code.DATA_SCOPE_FORBIDDEN, detail);
    }
}
