package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
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
 * 提现服务（F-07/F-08）：申请即冻结（FREEZE），审批通过即实际出账（状态 done），驳回解冻（UNFREEZE）。
 *
 * 钱包余额口径（与退款严格隔离）：
 *   累计结算入账(SETTLE IN) − 已提现(待审 pending + 已办 done)
 * 退款(REFUND)流水不计入钱包，避免买家原路退款污染卖家可提现余额。
 * 余额以 Withdrawal 表为权威来源，FundFlow 仅作流水展示与审计。
 */
@Service
public class WithdrawalService {

    private final WithdrawalMapper withdrawalMapper;
    private final FundFlowMapper fundFlowMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public WithdrawalService(WithdrawalMapper withdrawalMapper, FundFlowMapper fundFlowMapper,
                             UserMapper userMapper, NotificationService notificationService) {
        this.withdrawalMapper = withdrawalMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /** 累计结算入账（SETTLE IN 流水）。 */
    public long settledTotal(Long userId) {
        return fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getUserId, userId)
                        .eq(FundFlow::getDirection, "IN")
                        .eq(FundFlow::getType, "SETTLE"))
                .stream().mapToLong(FundFlow::getAmount).sum();
    }

    /** 已提现（待审 + 已办）金额，从 Withdrawal 表权威计算。 */
    private long reservedTotal(Long userId) {
        return withdrawalMapper.selectList(new LambdaQueryWrapper<Withdrawal>()
                        .eq(Withdrawal::getUserId, userId)
                        .in(Withdrawal::getStatus, List.of("pending", "done")))
                .stream().mapToLong(Withdrawal::getAmount).sum();
    }

    /** 可提现余额 = 累计结算入账 − 已提现（待审 + 已办）。 */
    public long withdrawableBalance(Long userId) {
        return settledTotal(userId) - reservedTotal(userId);
    }

    /** 冻结中金额（待审核提现）。 */
    public long frozenBalance(Long userId) {
        return withdrawalMapper.selectList(new LambdaQueryWrapper<Withdrawal>()
                        .eq(Withdrawal::getUserId, userId)
                        .eq(Withdrawal::getStatus, "pending"))
                .stream().mapToLong(Withdrawal::getAmount).sum();
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
        long balance = withdrawableBalance(userId);
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

        // F-07 提现通知：申请提交（best-effort）
        notificationService.notify(userId, NotificationType.WITHDRAW_APPLY, ff.getBizNo(), "提现申请已提交",
                "您的提现申请 " + (amount / 100.0) + " 元已提交，等待审核");
        return w;
    }

    /** 审批通过（实际出账）。钱包余额随 Withdrawal 状态(done)自动从可提现中扣除。 */
    @Transactional
    public Withdrawal approve(Long id) {
        Withdrawal w = withdrawalMapper.selectById(id);
        if (w == null) {
            throw new BizException(Code.NOT_FOUND, "提现单不存在");
        }
        if (!"pending".equals(w.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "该提现单不可审批");
        }
        Withdrawal upd = new Withdrawal();
        upd.setId(id);
        upd.setStatus("done");
        upd.setDoneAt(LocalDateTime.now());
        withdrawalMapper.updateById(upd);

        // 实际出账流水：余额随 Withdrawal 状态(done)自动从可提现中扣除
        long balance = withdrawableBalance(w.getUserId());
        FundFlow out = new FundFlow();
        out.setBizNo("WD" + IdGenerator.fundNo());
        out.setUserId(w.getUserId());
        out.setDirection("OUT");
        out.setAmount(w.getAmount());
        out.setType("WITHDRAW");
        out.setBalanceAfter(balance);
        fundFlowMapper.insert(out);

        // F-07 提现通知：审批通过（best-effort）
        notificationService.notify(w.getUserId(), NotificationType.WITHDRAW_APPROVE, "WD" + w.getId(), "提现已通过",
                "您的提现 " + (w.getAmount() / 100.0) + " 元已审核通过，正在出账");
        return withdrawalMapper.selectById(id);
    }

    /** 驳回（解冻：以 UNFREEZE 入金流水冲回冻结金额，余额恢复）。 */
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

        long balance = withdrawableBalance(w.getUserId());
        FundFlow ff = new FundFlow();
        ff.setBizNo("WD" + IdGenerator.fundNo());
        ff.setUserId(w.getUserId());
        ff.setDirection("IN");
        ff.setAmount(w.getAmount());
        ff.setType("UNFREEZE");
        ff.setBalanceAfter(balance);
        fundFlowMapper.insert(ff);

        // F-07 提现通知：驳回（best-effort）
        notificationService.notify(w.getUserId(), NotificationType.WITHDRAW_REJECT, "WD" + w.getId(), "提现被驳回",
                "您的提现 " + (w.getAmount() / 100.0) + " 元未通过审核，金额已解冻");
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
