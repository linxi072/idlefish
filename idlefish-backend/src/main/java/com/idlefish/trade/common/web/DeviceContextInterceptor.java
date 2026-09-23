package com.idlefish.trade.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 设备上下文拦截器：在每个请求处理前提取设备信号并写入 {@link DeviceContexts}，
 * 使风控埋点（TrackService）与设备指纹采集可无感知获取设备维度信息。
 */
@Component
public class DeviceContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        DeviceContexts.set(DeviceContexts.fromRequest(request));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DeviceContexts.clear();
    }
}
