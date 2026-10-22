package com.idlefish.trade.local.provider;

import lombok.Data;

/**
 * 第三方调度结果（ITER-F05 / REQ-06、REQ-09）。
 *
 * <p>纪律：{@code success=false} 时必须携带明确的 {@code failReason}，由上层转换为
 * {@code Code.LOCAL_DELIVERY_UNAVAILABLE} 返回给用户——禁止在无运单的情况下伪造成功。
 */
@Data
public class DispatchResult {

    private boolean success;
    /** 第三方运单号（成功时非空）。 */
    private String providerNo;
    private String courierName;
    private String courierPhone;
    /** 失败原因（对用户可展示）。 */
    private String failReason;

    public static DispatchResult ok(String providerNo, String courierName, String courierPhone) {
        DispatchResult r = new DispatchResult();
        r.setSuccess(true);
        r.setProviderNo(providerNo);
        r.setCourierName(courierName);
        r.setCourierPhone(courierPhone);
        return r;
    }

    public static DispatchResult fail(String reason) {
        DispatchResult r = new DispatchResult();
        r.setSuccess(false);
        r.setFailReason(reason);
        return r;
    }
}
