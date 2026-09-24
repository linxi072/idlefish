package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 提现/钱包一致性单测（F-07/F-08）：冻结扣减、驳回解冻恢复、通过永久扣减、退款不污染钱包、余额不足拦截。
 * 运行环境：MySQL（schema-mysql.sql + data-mysql.sql 自动初始化）。
 */
@SpringBootTest
class WithdrawalConsistencyTest {

    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private FundFlowMapper fundFlowMapper;
    @Autowired
    private UserMapper userMapper;

    private Long uid;

    @BeforeEach
    void setup() {
        uid = System.nanoTime();
        User u = new User();
        u.setId(uid);
        u.setNickname("wd-tester");
        u.setStatus(0);
        u.setRealNameVerified(0);
        u.setCreditScore(0);
        userMapper.insert(u);

        // 模拟结算入账 100000 分（1000 元）
        FundFlow ff = new FundFlow();
        ff.setBizNo("S" + uid);
        ff.setUserId(uid);
        ff.setDirection("IN");
        ff.setAmount(100000L);
        ff.setType("SETTLE");
        ff.setBalanceAfter(100000L);
        fundFlowMapper.insert(ff);
    }

    @Test
    @DisplayName("申请扣减可提现 + 冻结，驳回后全额恢复")
    void applyReducesAndRejectRestores() {
        assertEquals(100000L, withdrawalService.withdrawableBalance(uid));
        Withdrawal w = withdrawalService.apply(uid, 30000L, "6225888812345678");
        assertEquals(70000L, withdrawalService.withdrawableBalance(uid));
        assertEquals(30000L, withdrawalService.frozenBalance(uid));

        withdrawalService.reject(w.getId());
        assertEquals(100000L, withdrawalService.withdrawableBalance(uid));
        assertEquals(0L, withdrawalService.frozenBalance(uid));
    }

    @Test
    @DisplayName("申请通过后余额永久扣减（资金已出账），冻结清零")
    void applyThenApproveReducesPermanently() {
        Withdrawal w = withdrawalService.apply(uid, 30000L, "6225888812345678");
        assertEquals(70000L, withdrawalService.withdrawableBalance(uid));
        withdrawalService.approve(w.getId());
        assertEquals(70000L, withdrawalService.withdrawableBalance(uid));
        assertEquals(0L, withdrawalService.frozenBalance(uid));
    }

    @Test
    @DisplayName("退款 REFUND 流水不计入钱包余额与累计结算")
    void refundDoesNotAffectWallet() {
        FundFlow rf = new FundFlow();
        rf.setBizNo("R" + uid);
        rf.setUserId(uid);
        rf.setDirection("IN");
        rf.setAmount(50000L);
        rf.setType("REFUND");
        rf.setBalanceAfter(0L);
        fundFlowMapper.insert(rf);

        assertEquals(100000L, withdrawalService.withdrawableBalance(uid));
        assertEquals(100000L, withdrawalService.settledTotal(uid));
    }

    @Test
    @DisplayName("可提现余额不足时申请提现抛 BALANCE_NOT_ENOUGH")
    void insufficientBalance() {
        assertThrows(BizException.class, () -> withdrawalService.apply(uid, 200000L, "acct"));
    }
}
