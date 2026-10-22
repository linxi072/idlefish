package com.idlefish.trade.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.idlefish.trade.datascope.handler.DataScopeDataPermissionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：乐观锁 + 分页（MySQL）+ 数据范围权限（ITER-T05 / REQ-02）。
 * 乐观锁保障订单状态机/结算在并发确认收货、超时关单等场景下的资金安全。
 * 数据范围拦截器基于 {@link DataScopeDataPermissionHandler}，对配置了 org_id 的表按当前
 * 管理员可见机构集合注入 {@code org_id IN (...)}，无上下文/ALL 范围时放行全部。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataScopeDataPermissionHandler dataScopeHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁必须注册，否则 @Version 形同虚设，存在并发双重结算资损风险
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 数据范围：必须注册在分页之后、业务查询之前；MyBatis-Plus 按声明顺序串联
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor();
        dataPermissionInterceptor.setDataPermissionHandler(dataScopeHandler);
        interceptor.addInnerInterceptor(dataPermissionInterceptor);
        return interceptor;
    }
}
