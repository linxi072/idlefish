package com.idlefish.trade.common.lock;

import com.idlefish.trade.common.observability.MetricsRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 分布式锁单测（纯 Mockito，离线可跑，不依赖真实 Redis）。
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RedisDistributedLockImpl lock;

    @BeforeEach
    void setUp() {
        lock = new RedisDistributedLockImpl(redisTemplate, new MetricsRegistry());
    }

    @Test
    void tryLock_returnsToken_whenAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        String token = lock.tryLock("k1", 100L, 5000L);

        assertNotNull(token, "获取成功应返回 token，供释放时校验身份");
    }

    @Test
    void tryLock_returnsNull_whenTimeout() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        // waitMs=0：仅尝试一次即超时，避免测试长时间阻塞
        String token = lock.tryLock("k1", 0L, 5000L);

        assertNull(token, "等待超时未拿到锁应返回 null，由调用方降级");
    }

    @Test
    void unlock_returnsTrue_whenTokenMatches() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);

        assertTrue(lock.unlock("k1", "token-1"));
    }

    @Test
    void unlock_returnsFalse_whenTokenMismatch() {
        // Lua 校验不通过时返回 0：绝不允许删掉他人的锁
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

        assertFalse(lock.unlock("k1", "stale-token"));
    }

    @Test
    void tryLock_returnsNull_whenRedisDown() {
        // Redis 不可用：降级（返回 null），绝不阻断主流程
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertNull(lock.tryLock("k1", 0L, 5000L));
    }

    @Test
    void unlock_returnsFalse_whenRedisDown() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertFalse(lock.unlock("k1", "token-1"), "释放失败不抛异常，租约到期会自动释放");
    }

    @Test
    void unlock_returnsFalse_whenTokenNull() {
        assertFalse(lock.unlock("k1", null));
        assertFalse(lock.unlock(null, "token-1"));
    }

    @Test
    void lockKeyBuilder_buildsExpectedKey() {
        assertEquals("idlefish:lock:item:10088", LockKeyBuilder.item(10088L));
    }
}
