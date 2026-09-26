package com.idlefish.trade.common.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceContextTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void ensure_generates_when_absent() {
        MDC.clear();
        String t = TraceContext.ensure();
        assertNotNull(t);
        assertEquals(32, t.length());
        assertEquals(t, MDC.get(TraceContext.MDC_KEY));
    }

    @Test
    void ensure_keeps_existing() {
        MDC.put(TraceContext.MDC_KEY, "existing");
        assertEquals("existing", TraceContext.ensure());
    }

    @Test
    void wrap_propagates_to_child_thread() throws Exception {
        MDC.put(TraceContext.MDC_KEY, "parent-trace");
        AtomicReference<String> childTrace = new AtomicReference<>();
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            ex.submit(TraceContext.wrap(() -> childTrace.set(MDC.get(TraceContext.MDC_KEY)))).get();
        } finally {
            ex.shutdown();
        }
        assertEquals("parent-trace", childTrace.get());
    }

    @Test
    void wrap_clears_in_child_when_parent_empty() throws Exception {
        MDC.clear();
        AtomicReference<String> childTrace = new AtomicReference<>("unset");
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            ex.submit(TraceContext.wrap(() -> childTrace.set(MDC.get(TraceContext.MDC_KEY)))).get();
        } finally {
            ex.shutdown();
        }
        assertNull(childTrace.get());
    }
}
