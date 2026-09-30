package com.idlefish.trade.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.lock.DistributedLock;
import com.idlefish.trade.notify.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RealWxSubscribeChannelImpl 单测（纯 Mockito + 真实 Jackson，离线可跑）。
 * 验证：① 5 参构造接线正常；② 订阅未启用时 send 提前返回且不触达锁；③ 启用时经 Redis 锁单飞刷新 token；④ 锁不可用时降级不抛异常。
 */
@ExtendWith(MockitoExtension.class)
class RealWxSubscribeChannelImplTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private UserOpenidResolver openidResolver;
    @Mock
    private DistributedLock lock;

    private RealWxSubscribeChannelImpl channelWith(boolean enabled) {
        IdlefishProperties props = mock(IdlefishProperties.class);
        IdlefishProperties.Notify notify = mock(IdlefishProperties.Notify.class);
        IdlefishProperties.Notify.Subscribe sub = mock(IdlefishProperties.Notify.Subscribe.class);
        IdlefishProperties.Login login = mock(IdlefishProperties.Login.class);
        when(props.getNotify()).thenReturn(notify);
        when(notify.getSubscribe()).thenReturn(sub);
        when(sub.isEnabled()).thenReturn(enabled);
        // 以下 stub 仅在「启用且需刷新 token」路径被使用；未启用时 send 提前返回不会触达，
        // 用 lenient() 避免严格 stub 误报 UnnecessaryStubbing。
        lenient().when(sub.getTemplates()).thenReturn(Map.of("order_paid", "tpl_123"));
        lenient().when(props.getLogin()).thenReturn(login);
        lenient().when(login.getAppid()).thenReturn("appid");
        lenient().when(login.getSecret()).thenReturn("secret");
        return new RealWxSubscribeChannelImpl(props, restTemplate, new ObjectMapper(), openidResolver, lock);
    }

    private NotifyMessage sampleMsg() {
        return NotifyMessage.builder()
                .userId(1L).type(NotificationType.ORDER_PAID).title("t").content("c").build();
    }

    @Test
    void send_returnsEarly_whenSubscribeDisabled_andNeverTouchesLock() {
        RealWxSubscribeChannelImpl ch = channelWith(false);
        ch.send(sampleMsg());
        verify(lock, never()).tryLock(anyString(), anyLong(), anyLong());
    }

    @Test
    void send_acquiresAndReleasesLock_whenSubscribeEnabled() {
        when(lock.tryLock(anyString(), anyLong(), anyLong())).thenReturn("token-1");
        when(openidResolver.resolveOpenid(1L)).thenReturn("openid-1");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"access_token\":\"tok-abc\",\"expires_in\":7200}");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("{}");

        RealWxSubscribeChannelImpl ch = channelWith(true);
        ch.send(sampleMsg());

        verify(lock, times(1)).tryLock(anyString(), anyLong(), anyLong());
        verify(lock, times(1)).unlock(anyString(), eq("token-1"));
    }

    @Test
    void send_degradesWithoutThrowing_whenLockUnavailable() {
        // Redis 不可用 / 超时：tryLock 返回 null → 退化本实例 synchronized 单飞刷新，不应抛异常阻断主流程
        when(lock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(null);
        when(openidResolver.resolveOpenid(1L)).thenReturn("openid-1");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"access_token\":\"tok-abc\",\"expires_in\":7200}");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("{}");

        RealWxSubscribeChannelImpl ch = channelWith(true);
        assertDoesNotThrow(() -> ch.send(sampleMsg()));
        // 降级路径不应调用 unlock（从未拿到锁）
        verify(lock, never()).unlock(anyString(), anyString());
    }
}
