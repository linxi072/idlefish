package com.idlefish.trade.admin.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记控制器参数为当前登录管理员（由 AdminAuthInterceptor 注入 request 属性）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAdmin {
}
