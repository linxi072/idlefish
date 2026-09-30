package com.idlefish.trade.common.idempotent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.idempotent.entity.IdempotentRecord;
import com.idlefish.trade.common.idempotent.mapper.IdempotentMapper;
import com.idlefish.trade.common.observability.MetricsRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等服务单测（纯 Mockito，离线可跑）。
 * <p>
 * 覆盖三态流转：首次执行 / 重复回放 / 处理中拒绝 / 超时接管 / 失败重试，
 * 以及业务异常后记录仍被标记（证明独立事务生效，不会随业务回滚丢失）。
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentServiceTest {

    @Mock
    private IdempotentMapper mapper;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private TransactionStatus txStatus;

    private IdempotentService service;

    /**
     * 纯单测（无 MyBatis 配置）下必须显式注册实体元信息，
     * 否则 LambdaQueryWrapper / LambdaUpdateWrapper 因缺少 lambda 缓存而无法构造。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IdempotentRecord.class);
    }

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        service = new IdempotentService(mapper, new ObjectMapper(), new MetricsRegistry(), txManager);
    }

    @Test
    void execute_firstTime_invokesSupplierAndMarksSuccess() {
        String r = service.execute("order.create", "2001:10088", 30, String.class, () -> "R001");

        assertEquals("R001", r);
        verify(mapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void execute_duplicate_replaysFirstResult_withoutInvokingBusiness() {
        when(mapper.insert(any(IdempotentRecord.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(record(IdempotentStatus.SUCCESS.name(), "\"R001\"", false));

        AtomicInteger calls = new AtomicInteger();
        String r = service.execute("order.create", "2001:10088", 30, String.class, () -> {
            calls.incrementAndGet();
            return "R002";
        });

        assertEquals("R001", r, "重复请求应回放首次结果");
        assertEquals(0, calls.get(), "重复请求绝不能再次执行业务");
    }

    @Test
    void execute_processing_rejectsDuplicate() {
        when(mapper.insert(any(IdempotentRecord.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(record(IdempotentStatus.PROCESSING.name(), null, false));

        BizException ex = assertThrows(BizException.class,
                () -> service.execute("order.create", "2001:10088", 30, String.class, () -> "R001"));

        assertEquals(Code.BIZ_PROCESSING.getCode(), ex.getCode());
    }

    @Test
    void execute_processingExpired_takesOverAndExecutes() {
        when(mapper.insert(any(IdempotentRecord.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(record(IdempotentStatus.PROCESSING.name(), null, true));
        when(mapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        String r = service.execute("order.create", "2001:10088", 30, String.class, () -> "R003");

        assertEquals("R003", r, "PROCESSING 超时后应允许接管执行");
    }

    @Test
    void execute_failed_deletesAndRetries() {
        when(mapper.insert(any(IdempotentRecord.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(record(IdempotentStatus.FAILED.name(), null, false));

        String r = service.execute("order.create", "2001:10088", 30, String.class, () -> "R004");

        assertEquals("R004", r, "FAILED 应删除后重新占用，保证用户可重试");
        verify(mapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void execute_businessException_marksFailedAndRethrows() {
        assertThrows(IllegalStateException.class, () ->
                service.execute("order.create", "2001:10088", 30, String.class, () -> {
                    throw new IllegalStateException("boom");
                }));

        // 关键：业务异常后记录被标记 FAILED（独立事务写入，不随业务回滚丢失）
        verify(mapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void buildKey_hashesWhenTooLong() {
        String k = service.buildKey("order.create", "x".repeat(300));

        assertTrue(k.length() <= 128, "超长 key 必须压缩到索引列长度内");
        assertTrue(k.startsWith("order.create:"));
    }

    private IdempotentRecord record(String status, String result, boolean expired) {
        IdempotentRecord r = new IdempotentRecord();
        r.setIdemKey("order.create:2001:10088");
        r.setBizType("order.create");
        r.setStatus(status);
        r.setResult(result);
        r.setAttempt(1);
        r.setExpireAt(expired ? LocalDateTime.now().minusMinutes(5) : LocalDateTime.now().plusMinutes(5));
        return r;
    }
}
