package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.WithdrawalMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    private WithdrawalMapper withdrawalMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MetricsRegistry metrics;
    @Mock
    private FundEscrowService fundEscrowService;
    @InjectMocks
    private WithdrawalService service;

    private User userWithOpenid() {
        User u = new User();
        u.setId(2L);
        u.setWxOpenid("oXXX");
        return u;
    }

    private Withdrawal pendingWithdrawal() {
        Withdrawal w = new Withdrawal();
        w.setId(1L);
        w.setUserId(2L);
        w.setAmount(1000L);
        w.setStatus("pending");
        return w;
    }

    @Test
    void approve_success_writesDoneAndFlow() {
        when(withdrawalMapper.selectById(1L)).thenReturn(pendingWithdrawal());
        when(userMapper.selectById(2L)).thenReturn(userWithOpenid());
        when(fundEscrowService.transfer("WD1", 1000L, "oXXX")).thenReturn("BILL1");
        when(withdrawalMapper.selectList(any())).thenReturn(List.of());
        when(fundFlowMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L);

        ArgumentCaptor<Withdrawal> cap = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalMapper).updateById(cap.capture());
        assertEquals("done", cap.getValue().getStatus());
        assertEquals("BILL1", cap.getValue().getTransferNo());
        verify(fundFlowMapper).insert(any(FundFlow.class));
        verify(notificationService).notify(eq(2L), eq(NotificationType.WITHDRAW_APPROVE), any(), any(), any());
        verify(metrics).increment("withdraw.approve");
    }

    @Test
    void approve_transferFailure_rollsBackAndAlerts() {
        when(withdrawalMapper.selectById(1L)).thenReturn(pendingWithdrawal());
        when(userMapper.selectById(2L)).thenReturn(userWithOpenid());
        when(fundEscrowService.transfer(anyString(), anyLong(), anyString()))
                .thenThrow(new BizException(Code.FUND_TRANSFER_FAILED, "channel down"));

        assertThrows(BizException.class, () -> service.approve(1L));

        ArgumentCaptor<Withdrawal> cap = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalMapper).updateById(cap.capture());
        assertEquals("pending", cap.getValue().getStatus()); // 保持冻结，余额未扣减
        assertNotNull(cap.getValue().getFailReason());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class)); // 不出款流水
        verify(notificationService).notify(eq(1L), eq(NotificationType.SYSTEM_ALERT), any(), any(), any());
        verify(notificationService).notify(eq(2L), eq(NotificationType.WITHDRAW_REJECT), any(), any(), any());
        verify(metrics).increment("withdraw.transfer.failure");
    }
}
