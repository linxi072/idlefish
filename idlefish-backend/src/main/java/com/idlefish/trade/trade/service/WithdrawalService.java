package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.WithdrawalMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提现服务（PRD §F4）：申请即冻结（FREEZE），审批通过即实际出账（WITHDRAW）。
 * 资金冻结/出账均写入 t_fund_flow，保证资金变动可追溯。
 */
@Service
public class WithdrawalService {

    private final WithdrawalMapper withdrawalMapper;
    private final FundFlowMapper fundFlowMapper;
    private final UserMapper userMapper;

    public WithdrawalService(WithdrawalMapper withdrawalMapper, FundFlowMapper fundFlowMapper, UserMapper userMapper) {
        this.withdrawalMapper = withdrawalMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.userMapper = userMapper;
    }

    /** 当前用户可提现余额（累计 SETTLE 入账 - 累计 FREEZE/WITHDRAW 出账）。 */
    public long balanceOf(Long userId) {
        long in = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getUserId, userId).eq(FundFlow::getDirection, "IN"))
                .stream().mapToLong(FundFlow::getAmount).sum();
        long out = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getUserId, userId).eq(FundFlow::getDirection, "OUT"))
                .stream().mapToLong(FundFlow::getAmount).sum();
        return in - out;
    }

    /** 申请提现（先冻结）。 */
    @Transactional
    public Withdrawal apply(Long userId, Long amount, String account) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(Code.USER_NOT_FOUND);
        }
        if (amount == null || amount <= 0) {
            throw new BizException(Code.PARAM_INVALID, "提现金额无效");
        }
        long balance = balanceOf(userId);
        if (balance < amount) {
            throw new BizException(Code.BALANCE_NOT_ENOUGH, "可提现余额不足");
        }
        Withdrawal w = new Withdrawal();
        w.setUserId(userId);
        w.setAmount(amount);
        w.setAccount(mask(account));
        w.setStatus("pending");
        withdrawalMapper.insert(w);

        FundFlow ff = new FundFlow();
        ff.setBizNo("WD" + IdGenerator.fundNo());
        ff.setUserId(userId);
        ff.setDirection("OUT");
        ff.setAmount(amount);
        ff.setType("FREEZE");
        ff.setBalanceAfter(balance - amount);
        fundFlowMapper.insert(ff);
        return w;
    }

    /** 审批通过（实际出账）。 */
    @Transactional
    public Withdrawal approve(Long id) {
        Withdrawal w = withdrawalMapper.selectById(id);
        if (w == null) {
            throw new BizException(Code.NOT_FOUND, "提现单不存在");
        }
        if (!"pending".equals(w.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "该提现单不可审批");
        }
        long balance = balanceOf(w.getUserId());
        Withdrawal upd = new Withdrawal();
        upd.setId(id);
        upd.setStatus("done");
        upd.setDoneAt(LocalDateTime.now());
        withdrawalMapper.updateById(upd);

        FundFlow ff = new FundFlow();
        ff.setBizNo("WD" + IdGenerator.fundNo());
        ff.setUserId(w.getUserId());
        ff.setDirection("OUT");
        ff.setAmount(w.getAmount());
        ff.setType("WITHDRAW");
        ff.setBalanceAfter(balance - w.getAmount());
        fundFlowMapper.insert(ff);
        return withdrawalMapper.selectById(id);
    }

    /** 驳回（解冻：以负向 FREEZE 冲回不可用，演示中以状态标记）。 */
    @Transactional
    public Withdrawal reject(Long id) {
        Withdrawal w = withdrawalMapper.selectById(id);
        if (w == null) {
            throw new BizException(Code.NOT_FOUND, "提现单不存在");
        }
        if (!"pending".equals(w.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "该提现单不可驳回");
        }
        Withdrawal upd = new Withdrawal();
        upd.setId(id);
        upd.setStatus("rejected");
        withdrawalMapper.updateById(upd);
        return w;
    }

    public List<Withdrawal> list() {
        return withdrawalMapper.selectList(new LambdaQueryWrapper<Withdrawal>().orderByDesc(Withdrawal::getCreatedAt));
    }

    /** 账号脱敏：仅保留前 2 后 2。 */
    private String mask(String account) {
        if (account == null || account.length() <= 4) {
            return account;
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }
}
