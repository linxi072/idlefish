package com.idlefish.trade.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 设备上下文持有器：
 * - 请求内透传：由 {@link DeviceContextInterceptor} 写入 ThreadLocal，业务侧通过 {@link #current()} 获取；
 * - 测试/异步场景：可显式 {@link #set(DeviceContext)} 注入，便于离线单元/集成测试驱动风控规则。
 */
public final class DeviceContexts {

    private static final ThreadLocal<DeviceContext> HOLDER = new ThreadLocal<>();
    private static final String REQ_ATTR = "deviceContext";

    private DeviceContexts() {
    }

    /** 从当前请求上下文获取设备上下文（无则返回 null）。 */
    public static DeviceContext current() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return HOLDER.get();
        }
        HttpServletRequest req = attrs.getRequest();
        DeviceContext dc = (DeviceContext) req.getAttribute(REQ_ATTR);
        if (dc == null) {
            dc = fromRequest(req);
            req.setAttribute(REQ_ATTR, dc);
        }
        return dc;
    }

    /** 从请求头/地址提取设备信号（兜底生成 deviceId）。 */
    public static DeviceContext fromRequest(HttpServletRequest req) {
        String deviceId = req.getHeader("X-Device-Id");
        String ua = req.getHeader("User-Agent");
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = req.getRemoteAddr();
        }
        if (deviceId == null || deviceId.isBlank()) {
            // 兜底设备标识：UA + IP 的稳定哈希（非唯一，仅作降级）
            deviceId = "d_auto_" + Integer.toHexString((ua == null ? "" : ua + "|" + ip).hashCode());
        }
        return DeviceContext.builder().deviceId(deviceId).ua(ua).ip(ip).build();
    }

    public static void set(DeviceContext dc) {
        HOLDER.set(dc);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
