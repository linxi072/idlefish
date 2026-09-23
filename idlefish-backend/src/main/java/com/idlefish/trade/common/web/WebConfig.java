package com.idlefish.trade.common.web;

import com.idlefish.trade.admin.web.AdminAuthInterceptor;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

/**
 * Web 配置：注册鉴权拦截器与 @CurrentUser 参数解析器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CurrentAdminArgumentResolver currentAdminArgumentResolver;
    private final DeviceContextInterceptor deviceContextInterceptor;

    public WebConfig(AuthInterceptor authInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver,
                     AdminAuthInterceptor adminAuthInterceptor,
                     CurrentAdminArgumentResolver currentAdminArgumentResolver,
                     DeviceContextInterceptor deviceContextInterceptor) {
        this.authInterceptor = authInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.currentAdminArgumentResolver = currentAdminArgumentResolver;
        this.deviceContextInterceptor = deviceContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/search/**",
                        "/api/category/**",
                        "/api/item/buyer/**",
                        "/api/item/detail/**",
                        "/api/pay/notify",
                        "/api/pay/notify/v3",
                        "/api/mq/**",
                        "/api/admin/**",
                        "/error",
                        "/actuator/**");

        // 后台独立鉴权：角色由服务端签发，杜绝客户端伪造 X-Admin-Role 提权
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");

        // 设备上下文提取（风控指纹）：对所有 /api/** 生效
        registry.addInterceptor(deviceContextInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
        resolvers.add(currentAdminArgumentResolver);
    }

    /**
     * 静态资源映射：将上传目录以 /uploads/** 对外提供（与 idlefish.file.base-url 一致）。
     * 生产环境应改用 OSS/S3 等对象存储，此处仅用于本地演示。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = Paths.get(System.getProperty("user.dir"), "uploads")
                .toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir);
    }
}
