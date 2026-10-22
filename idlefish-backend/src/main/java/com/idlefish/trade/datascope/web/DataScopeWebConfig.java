package com.idlefish.trade.datascope.web;

import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.datascope.service.DataScopeService;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 数据范围 Web 配置（ITER-T05）：注册 {@link DataScopeInterceptor}。
 * 独立于 {@code WebConfig}（受保护文件，不在此触碰），以最低优先级注册，
 * 确保其在既有认证拦截器之后装配上下文，并在请求结束时清理 ThreadLocal。
 */
@Configuration
public class DataScopeWebConfig implements WebMvcConfigurer, Ordered {

    private final JwtUtil jwtUtil;
    private final DataScopeService dataScopeService;

    public DataScopeWebConfig(JwtUtil jwtUtil, DataScopeService dataScopeService) {
        this.jwtUtil = jwtUtil;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DataScopeInterceptor(jwtUtil, dataScopeService))
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
