package com.idlefish.trade.common.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MdcTaskDecoratorTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void decorate_copies_mdc_to_worker_thread() throws InterruptedException {
        MDC.put(TraceContext.MDC_KEY, "abc");
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get(TraceContext.MDC_KEY)));

        Thread worker = new Thread(decorated);
        MDC.clear(); // 模拟提交后父线程上下文变化
        worker.start();
        worker.join(2000);

        assertEquals("abc", seen.get());
    }
}
