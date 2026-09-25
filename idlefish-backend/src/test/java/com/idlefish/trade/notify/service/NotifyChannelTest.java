package com.idlefish.trade.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.im.ws.WsSessionManager;
import com.idlefish.trade.notify.channel.ChannelType;
import com.idlefish.trade.notify.channel.InAppNotifyChannel;
import com.idlefish.trade.notify.channel.RealSmsNotifyChannelImpl;
import com.idlefish.trade.notify.channel.NotifyChannel;
import com.idlefish.trade.notify.channel.NotifyMessage;
import com.idlefish.trade.notify.channel.PushNotifyChannel;
import com.idlefish.trade.notify.entity.Notification;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-05 通知渠道抽象与路由纯单测（无 Spring 上下文，CI `mvn test` 执行）。
 * 覆盖：渠道路由策略、分发只命中匹配渠道、InApp 落库、Push 离线跳过/在线推送、真实短信 fail-closed。
 */
class NotifyChannelTest {

    @Test
    void routing_strategy_by_type() {
        NotificationService svc = new NotificationService(null, List.of());
        assertTrue(svc.resolveChannels(NotificationType.REFUND_PLATFORM).contains(ChannelType.SMS));
        assertFalse(svc.resolveChannels(NotificationType.ORDER_PAID).contains(ChannelType.SMS));
        assertTrue(svc.resolveChannels(NotificationType.ORDER_PAID).contains(ChannelType.IN_APP));
        assertTrue(svc.resolveChannels(NotificationType.ORDER_PAID).contains(ChannelType.PUSH));
    }

    @Test
    void dispatch_routes_to_matching_channels_only() {
        RecordingChannel inApp = new RecordingChannel(ChannelType.IN_APP);
        RecordingChannel push = new RecordingChannel(ChannelType.PUSH);
        RecordingChannel sms = new RecordingChannel(ChannelType.SMS);
        NotificationService svc = new NotificationService(null, Arrays.asList(inApp, push, sms));

        svc.notify(1L, NotificationType.REFUND_PLATFORM, "R1", "平台介入", "内容");
        svc.notify(2L, NotificationType.ORDER_PAID, "O1", "支付成功", "内容");

        assertEquals(2, inApp.received.size());
        assertEquals(2, push.received.size());
        assertEquals(1, sms.received.size());
        assertEquals(NotificationType.REFUND_PLATFORM, sms.received.get(0).getType());
    }

    @Test
    void inApp_channel_persists_notification() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        InAppNotifyChannel ch = new InAppNotifyChannel(mapper);
        ch.send(NotifyMessage.builder().userId(5L).type(NotificationType.ORDER_PAID)
                .bizId("O1").title("t").content("c").build());

        var captor = forClass(Notification.class);
        verify(mapper).insert(captor.capture());
        Notification n = captor.getValue();
        assertEquals(5L, n.getUserId());
        assertEquals("order_paid", n.getType());
        assertEquals(0, n.getRead());
        assertEquals("t", n.getTitle());
        assertEquals("c", n.getContent());
    }

    @Test
    void push_channel_skips_when_offline_and_sends_when_online() {
        WsSessionManager ws = mock(WsSessionManager.class);
        PushNotifyChannel push = new PushNotifyChannel(ws, new ObjectMapper(), new IdlefishProperties());

        when(ws.isOnline(9L)).thenReturn(false);
        push.send(NotifyMessage.builder().userId(9L).type(NotificationType.ORDER_PAID)
                .title("t").content("c").build());
        verify(ws, never()).send(any(), any());

        when(ws.isOnline(9L)).thenReturn(true);
        push.send(NotifyMessage.builder().userId(9L).type(NotificationType.ORDER_PAID)
                .title("t").content("c").build());
        verify(ws).send(eq(9L), contains("notification"));
    }

    @Test
    void realSms_channel_is_fail_closed_and_does_not_throw() {
        // 生产已移除 Mock 短信：真实渠道为 fail-closed，凭据缺失时回落日志，绝不外抛
        RealSmsNotifyChannelImpl sms = new RealSmsNotifyChannelImpl(new IdlefishProperties());
        assertEquals(ChannelType.SMS, sms.type());
        sms.send(NotifyMessage.builder().userId(1L).type(NotificationType.REFUND_PLATFORM)
                .title("x").content("y").build());
    }

    static class RecordingChannel implements NotifyChannel {
        private final ChannelType t;
        final List<NotifyMessage> received = new ArrayList<>();

        RecordingChannel(ChannelType t) {
            this.t = t;
        }

        @Override
        public ChannelType type() {
            return t;
        }

        @Override
        public void send(NotifyMessage msg) {
            received.add(msg);
        }
    }
}
