package com.idlefish.trade.user.service;

import com.idlefish.trade.common.util.CryptoUtil;
import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务单测：聚焦微信登录注册链路与 USER_REGISTERED 领域事件接线（事件驱动架构 T06E）。
 *
 * <p>采用纯 Mockito 离线范式（@InjectMocks 构造注入），不依赖任何外部组件（微信/DB/OSS）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private CryptoUtil cryptoUtil;
    @Mock private JwtUtil jwtUtil;
    @Mock private WechatLoginService wechatLoginService;
    @Mock private com.idlefish.trade.event.DomainEventPublisher eventPublisher;

    @InjectMocks
    private UserService service;

    @Test
    void loadOrCreateByOpenid_registersNewUserAndPublishesEvent() {
        // 新用户：selectOne 返回 null → 触发插入与 USER_REGISTERED 发布
        when(userMapper.selectOne(any())).thenReturn(null);
        // 模拟 MyBatis-Plus ASSIGN_ID：insert 前回填雪花主键，使 user.getId() 非空
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        User user = service.loadOrCreateByOpenid("openid_abc123");

        assertNotNull(user.getId());
        verify(userMapper, times(1)).insert(any(User.class));
        // 首次注册发布 USER_REGISTERED（聚合根 = 用户 id）
        verify(eventPublisher, times(1)).publish(
                eq(com.idlefish.trade.event.EventType.USER_REGISTERED),
                eq(String.valueOf(user.getId())),
                any());
    }

    @Test
    void loadOrCreateByOpenid_existingUserNoEvent() {
        // 已存在用户：直接返回，不插入、不发布事件
        User existing = new User();
        existing.setId(99L);
        existing.setWxOpenid("openid_abc123");
        when(userMapper.selectOne(any())).thenReturn(existing);

        User user = service.loadOrCreateByOpenid("openid_abc123");

        assertEquals(99L, user.getId());
        verify(userMapper, never()).insert(any(User.class));
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void loadOrCreateByOpenid_newUserDefaultCreditScore() {
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        User user = service.loadOrCreateByOpenid("openid_newuser");
        // 默认信用分 100、状态正常
        assertEquals(100, user.getCreditScore());
        assertEquals(0, user.getStatus());
        assertNull(user.getWxUnionid()); // 真实模式首次回填前为空
    }
}
