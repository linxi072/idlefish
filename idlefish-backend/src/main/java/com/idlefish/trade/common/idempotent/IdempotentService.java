package com.idlefish.trade.common.idempotent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.idempotent.entity.IdempotentRecord;
import com.idlefish.trade.common.idempotent.mapper.IdempotentMapper;
import com.idlefish.trade.common.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 幂等服务（三态：PROCESSING / SUCCESS / FAILED）。
 * <p>
 * 采用**编程式接入**而非 AOP：项目未引入 spring-boot-starter-aop，
 * 且需保证离线 {@code mvn -o} 可构建——新增依赖会破坏该约束。语义与 AOP 方案等价。
 * <p>
 * 正确性要点：<b>幂等记录必须写在独立事务（REQUIRES_NEW）中</b>，
 * 否则业务异常回滚会把记录一并抹掉，导致重试穿透、幂等彻底失效。
 * 为避免同类自调用导致 {@code @Transactional} 失效，此处统一使用 TransactionTemplate。
 */
@Slf4j
@Service
public class IdempotentService {

    /** idem_key 列长度上限（与 DDL VARCHAR(128) 对齐，utf8mb4 下索引 512B，安全）。 */
    private static final int MAX_KEY_LEN = 128;

    private final IdempotentMapper mapper;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    /** 独立事务模板：保证幂等记录写入不随业务事务回滚。 */
    private final TransactionTemplate newTx;

    public IdempotentService(IdempotentMapper mapper, ObjectMapper objectMapper,
                             MetricsRegistry metrics, PlatformTransactionManager txManager) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newTx = t;
    }

    /**
     * 幂等执行模板。
     *
     * @param bizType    业务类型（如 order.create）
     * @param bizKey     业务键（不含 bizType）
     * @param ttlSeconds PROCESSING 过期秒数（超时后允许被接管）
     * @param type       返回值类型，用于重复请求回放反序列化
     * @param supplier   业务执行体
     * @return 首次执行结果，或重复请求回放的首次结果
     */
    public <T> T execute(String bizType, String bizKey, long ttlSeconds, Class<T> type, Supplier<T> supplier) {
        String key = buildKey(bizType, bizKey);

        IdempotentRecord exist = tryInsert(key, bizType, ttlSeconds);
        if (exist != null) {
            String status = exist.getStatus();
            if (IdempotentStatus.PROCESSING.name().equals(status)) {
                if (exist.getExpireAt() != null && exist.getExpireAt().isAfter(LocalDateTime.now())) {
                    metrics.increment("idempotent.processing");
                    throw new BizException(Code.BIZ_PROCESSING);
                }
                // 已超时：CAS 接管，抢占失败则仍拒绝（避免两个请求同时执行）
                if (!takeOverIfExpired(key, ttlSeconds)) {
                    metrics.increment("idempotent.processing");
                    throw new BizException(Code.BIZ_PROCESSING);
                }
            } else if (IdempotentStatus.SUCCESS.name().equals(status)) {
                // 已完成：回放首次结果，绝不重复执行业务
                metrics.increment("idempotent.replay");
                return replay(exist, type);
            } else {
                // FAILED：删除后重新占用，保证用户可重试
                deleteByKey(key);
                if (tryInsert(key, bizType, ttlSeconds) != null) {
                    metrics.increment("idempotent.processing");
                    throw new BizException(Code.BIZ_PROCESSING);
                }
            }
        }

        try {
            T result = supplier.get();
            markSuccess(key, toJson(result));
            return result;
        } catch (RuntimeException e) {
            markFailed(key);
            throw e;
        }
    }

    // ===== 记录读写（全部走独立事务） =====

    /** 尝试占用（INSERT PROCESSING）；冲突时返回既有记录，成功返回 null。 */
    public IdempotentRecord tryInsert(String key, String bizType, long ttlSeconds) {
        return newTx.execute(status -> {
            try {
                IdempotentRecord rec = new IdempotentRecord();
                rec.setIdemKey(key);
                rec.setBizType(bizType);
                rec.setStatus(IdempotentStatus.PROCESSING.name());
                rec.setAttempt(1);
                rec.setExpireAt(LocalDateTime.now().plusSeconds(Math.max(1L, ttlSeconds)));
                rec.setCreateTime(LocalDateTime.now());
                rec.setUpdateTime(LocalDateTime.now());
                mapper.insert(rec);
                return null;
            } catch (DuplicateKeyException e) {
                metrics.increment("idempotent.conflict");
                return mapper.selectOne(new LambdaQueryWrapper<IdempotentRecord>()
                        .eq(IdempotentRecord::getIdemKey, key));
            }
        });
    }

    /** 超时接管：仅当仍处于 PROCESSING 且已过期才更新，返回是否抢占成功。 */
    public boolean takeOverIfExpired(String key, long ttlSeconds) {
        Boolean ok = newTx.execute(status -> mapper.update(null, new LambdaUpdateWrapper<IdempotentRecord>()
                .eq(IdempotentRecord::getIdemKey, key)
                .eq(IdempotentRecord::getStatus, IdempotentStatus.PROCESSING.name())
                .lt(IdempotentRecord::getExpireAt, LocalDateTime.now())
                .set(IdempotentRecord::getExpireAt, LocalDateTime.now().plusSeconds(Math.max(1L, ttlSeconds)))
                .setSql("attempt = attempt + 1")) > 0);
        return Boolean.TRUE.equals(ok);
    }

    /** 标记成功并记录结果快照（供回放）。 */
    public void markSuccess(String key, String resultJson) {
        newTx.execute(status -> {
            mapper.update(null, new LambdaUpdateWrapper<IdempotentRecord>()
                    .eq(IdempotentRecord::getIdemKey, key)
                    .set(IdempotentRecord::getStatus, IdempotentStatus.SUCCESS.name())
                    .set(IdempotentRecord::getResult, resultJson)
                    .set(IdempotentRecord::getUpdateTime, LocalDateTime.now()));
            metrics.increment("idempotent.success");
            return null;
        });
    }

    /** 标记失败（允许后续重试时删除重建）。 */
    public void markFailed(String key) {
        newTx.execute(status -> {
            mapper.update(null, new LambdaUpdateWrapper<IdempotentRecord>()
                    .eq(IdempotentRecord::getIdemKey, key)
                    .set(IdempotentRecord::getStatus, IdempotentStatus.FAILED.name())
                    .set(IdempotentRecord::getUpdateTime, LocalDateTime.now()));
            return null;
        });
    }

    /** 删除记录（FAILED 重试 / 清理任务用）。 */
    public void deleteByKey(String key) {
        newTx.execute(status -> {
            mapper.delete(new LambdaQueryWrapper<IdempotentRecord>()
                    .eq(IdempotentRecord::getIdemKey, key));
            return null;
        });
    }

    /** 清理过期记录：保留 7 天，兼顾对账与客诉排查。 */
    public int cleanupExpired(int retainDays) {
        Integer n = newTx.execute(status -> mapper.delete(new LambdaQueryWrapper<IdempotentRecord>()
                .lt(IdempotentRecord::getCreateTime, LocalDateTime.now().minusDays(Math.max(1, retainDays)))));
        return n == null ? 0 : n;
    }

    // ===== 辅助 =====

    /** 构建幂等键：超长时取 SHA-256 摘要，控制索引体积。 */
    public String buildKey(String bizType, String bizKey) {
        String raw = bizType + ":" + bizKey;
        if (raw.length() <= MAX_KEY_LEN) {
            return raw;
        }
        return bizType + ":" + sha256Hex(raw);
    }

    private <T> T replay(IdempotentRecord rec, Class<T> type) {
        String json = rec.getResult();
        if (json == null || json.isEmpty() || type == null) {
            throw new BizException(Code.BIZ_ERROR, "请求已处理，请查询结果");
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("[idempotent] replay failed, key={}", rec.getIdemKey(), e);
            throw new BizException(Code.BIZ_ERROR, "请求已处理，请查询结果");
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[idempotent] serialize result failed", e);
            return null;
        }
    }

    private String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
