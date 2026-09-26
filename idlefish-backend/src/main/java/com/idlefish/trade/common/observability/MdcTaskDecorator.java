package com.idlefish.trade.common.observability;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 透传装饰器（F-12.4）：将提交任务的线程的 MDC 上下文复制到执行线程，
 * 使 {@code @Async} / 线程池提交的异步任务日志带有与请求一致的 traceId。
 *
 * <p>用法：为 {@code ThreadPoolTaskExecutor} 设置 {@code setTaskDecorator(new MdcTaskDecorator())}。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
