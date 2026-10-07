package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.idempotent.IdempotentService;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.FundFlowBuilder;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.WithdrawalMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final MetricsRegistry metrics;
    private final FundEscrowService fundEscrowService;

    /** 幂等（F-18）：防连点/网络重试导致重复冻结与重复出款。 */
    private final IdempotentService idempotentService;

    /** 事务模板：显式事务边界（原 @Transactional 在拆分后内层方法上会因自调用失效）。 */
    private final TransactionTemplate txTemplate;

    /** 提现申请幂等业务类型。 */
    private static final String BIZ_WITHDRAW_APPLY = "withdraw.apply";

    /** 幂等 PROCESSING 过期秒数（短窗口，仅防连点/重试）。 */
    private static final long IDEMPOTENT_TTL_SECONDS = 10L;

    /** 幂等键时间窗（分钟级）：避免误杀用户稍后再次提现相同金额。 */
    private static final DateTimeFormatter MINUTE_WINDOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 系统告警接收者（管理员账号 userId）；资金出款失败告警发往此处。 */
    private static final long ADMIN_ALERT_USER_ID = 1L;

    public WithdrawalService(WithdrawalMapper withdrawalMapper, FundFlowMapper fundFlowMapper,
                             UserMapper userMapper, NotificationService notificationService,
                             MetricsRegistry metrics, FundEscrowService fundEscrowService,
                             IdempotentService idempotentService, PlatformTransactionManager txManager) {
        this.withdrawalMapper = withdrawalMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.fundEscrowService = fundEscrowService;
        this.idempotentService = idempotentService;
        this.txTemplate = new TransactionTemplate(txManager);
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

    /**
     * 申请提现（先冻结）。
     * <p>
     * 幂等（F-18）：同用户同金额在分钟级窗口内重复提交只执行一次，防止连点/网络重试造成重复冻结与重复出款。
     * <p>
     * 事务改用 TransactionTemplate 显式管理：原 {@code @Transactional} 若保留在拆分后的内层方法上
     * 会因同类自调用失效；且幂等记录必须写在独立事务中，否则业务回滚会抹掉记录导致幂等失效。
     */
    public Withdrawal apply(Long userId, Long amount, String account) {
        String bizKey = userId + ":" + amount + ":" + LocalDateTime.now().format(MINUTE_WINDOW_FMT);
        return idempotentService.execute(BIZ_WITHDRAW_APPLY, bizKey, IDEMPOTENT_TTL_SECONDS,
                Withdrawal.class, () -> txTemplate.execute(status -> doApply(userId, amount, account)));
    }

    /** 事务内提现逻辑（冻结 + 冻结流水 + 通知）。 */
    private Withdrawal doApply(Long userId, Long amount, String account) {
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

        String bizNo = "WD" + IdGenerator.fundNo();
        fundFlowMapper.insert(FundFlowBuilder.of(bizNo, userId, "OUT", "FREEZE", amount)
                .balanceAfter(balance - amount).build());

        // F-07 提现通知：申请提交（best-effort）
        notificationService.notify(userId, NotificationType.WITHDRAW_APPLY, bizNo, "提现申请已提交",
                "您的提现申请 " + (amount / 100.0) + " 元已提交，等待审核");
        metrics.increment("withdraw.apply");
        return w;
    }

    /**
     * 审批通过：调用真实出款（微信商家转账到零钱）。成功则置 done 并写出款流水；
     * 失败则保持 pending（余额仍冻结未扣减）、记录失败原因、向管理员与用户告警，并抛出以便管理端感知。
     * 以 "WD"+id 为幂等单号（RealWechatEscrowServiceImpl 内部先查后转），重试安全。
     * <p>注意：本方法不包裹 @Transactional。出款为外部调用，若失败须确保 failReason 落库（而非随事务回滚）；
     * 成功路径先置 done 态（资金已实际出账）再写流水，保证状态为权威来源。
     */
    public Withdrawal approve(Long id) {
        Withdrawal w = withdrawalMapper.selectById(id);
        if (w == null) {
            throw new BizException(Code.NOT_FOUND, "提现单不存在");
        }
        if (!"pending".equals(w.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "该提现单不可审批");
        }
        String outBizNo = "WD" + w.getId();
        String openid = resolveOpenid(w.getUserId());

        String transferNo;
        try {
            transferNo = fundEscrowService.transfer(outBizNo, w.getAmount(), openid);
        } catch (BizException e) {
            // 真实出款失败：回滚（保持 pending，资金仍冻结未扣减），记录原因并告警
            Withdrawal failUpd = new Withdrawal();
            failUpd.setStatus("pending");
            failUpd.setFailReason(truncate(e.getMessage()));
            // P0-5：以 CAS（仅当仍处于 pending 时）记录失败原因，避免竞态失败方覆盖成功方的 done 状态
            int failAffected = withdrawalMapper.update(failUpd, new LambdaQueryWrapper<Withdrawal>()
                    .eq(Withdrawal::getId, id).eq(Withdrawal::getStatus, "pending"));
            if (failAffected == 0) {
                throw e; // 已被其它线程/节点完成审批，放弃覆盖
            }
            metrics.increment("withdraw.transfer.failure");
            notificationService.notify(ADMIN_ALERT_USER_ID, NotificationType.SYSTEM_ALERT, "WD" + id, "提现出款失败",
                    "提现单 WD" + id + " 金额 " + (w.getAmount() / 100.0) + " 元出款失败：" + e.getMessage());
            notificationService.notify(w.getUserId(), NotificationType.WITHDRAW_REJECT, "WD" + id, "提现失败",
                    "您的提现 " + (w.getAmount() / 100.0) + " 元出款失败，金额已保持冻结，请稍后重试或联系客服");
            throw e;
        }

        // 出款成功：以 CAS（pending→done）推进，affected==1 才记账；并发审批竞态下仅胜出线程写资金流水，
        // 避免重复出款/重复记账，也防止失败方将已完成的 done 状态覆盖回 pending。
        Withdrawal doneUpd = new Withdrawal();
        doneUpd.setStatus("done");
        doneUpd.setTransferNo(transferNo);
        doneUpd.setDoneAt(LocalDateTime.now());
        int affected = withdrawalMapper.update(doneUpd, new LambdaQueryWrapper<Withdrawal>()
                .eq(Withdrawal::getId, id).eq(Withdrawal::getStatus, "pending"));
        if (affected == 0) {
            return withdrawalMapper.selectById(id); // 已被其它线程/节点审批完成
        }

        long balance = withdrawableBalance(w.getUserId());
        fundFlowMapper.insert(FundFlowBuilder.of("WD" + IdGenerator.fundNo(), w.getUserId(), "OUT", "WITHDRAW", w.getAmount())
                .balanceAfter(balance).build());

        // F-07 提现通知：审批通过（best-effort）
        notificationService.notify(w.getUserId(), NotificationType.WITHDRAW_APPROVE, "WD" + w.getId(), "提现已通过",
                "您的提现 " + (w.getAmount() / 100.0) + " 元已出账至微信零钱");
        metrics.increment("withdraw.approve");
        return withdrawalMapper.selectById(id);
    }

    /** 解析收款用户 openid（商家转账到零钱必填）。未绑定微信则无法出款。 */
    private String resolveOpenid(Long userId) {
        com.idlefish.trade.user.entity.User u = userMapper.selectById(userId);
        if (u == null || u.getWxOpenid() == null || u.getWxOpenid().isBlank()) {
            throw new BizException(Code.BIZ_ERROR, "用户未绑定微信 openid，无法出款");
        }
        return u.getWxOpenid();
    }

    /** 失败原因截断至 255 字符，匹配 t_withdrawal.fail_reason 列宽。 */
    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
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
        fundFlowMapper.insert(FundFlowBuilder.of("WD" + IdGenerator.fundNo(), w.getUserId(), "IN", "UNFREEZE", w.getAmount())
                .balanceAfter(balance).build());

        // F-07 提现通知：驳回（best-effort）
        notificationService.notify(w.getUserId(), NotificationType.WITHDRAW_REJECT, "WD" + w.getId(), "提现被驳回",
                "您的提现 " + (w.getAmount() / 100.0) + " 元未通过审核，金额已解冻");
        metrics.increment("withdraw.reject");
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
