package com.idlefish.trade.common.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备上下文：从 HTTP 请求提取的稳定设备信号，供风控引擎做设备指纹与规则判定。
 * - deviceId：客户端生成的稳定设备标识（缺失时由服务端按 UA+IP 兜底生成）。
 * - ua：User-Agent，含机型/系统/版本信息。
 * - ip：客户端真实 IP（取 X-Forwarded-For 首段，兼容直连）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceContext {

    private String deviceId;
    private String ua;
    private String ip;

    /** 是否携带可信设备 ID（客户端显式上报）。 */
    public boolean hasDeviceId() {
        return deviceId != null && !deviceId.isBlank();
    }
}
