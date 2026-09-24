package com.idlefish.trade.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.service.WithdrawalService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包接口（F-07/F-08）：余额（可提现/冻结/累计结算）、钱包流水、提现申请。
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WithdrawalService withdrawalService;
    private final FundFlowMapper fundFlowMapper;

    public WalletController(WithdrawalService withdrawalService, FundFlowMapper fundFlowMapper) {
        this.withdrawalService = withdrawalService;
        this.fundFlowMapper = fundFlowMapper;
    }

    /** 钱包余额概览。 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance(@CurrentUser LoginUser loginUser) {
        Long userId = loginUser.getUserId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("withdrawable", withdrawalService.withdrawableBalance(userId));
        m.put("frozen", withdrawalService.frozenBalance(userId));
        m.put("settledTotal", withdrawalService.settledTotal(userId));
        return Result.ok(m);
    }

    /** 钱包流水（仅钱包相关类型：SETTLE/FREEZE/WITHDRAW/UNFREEZE；退款 REFUND 不计入）。 */
    @GetMapping("/flows")
    public Result<IPage<FundFlow>> flows(@CurrentUser LoginUser loginUser,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Page<FundFlow> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<FundFlow> result = fundFlowMapper.selectPage(p, new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getUserId, loginUser.getUserId())
                .in(FundFlow::getType, List.of("SETTLE", "FREEZE", "WITHDRAW", "UNFREEZE"))
                .orderByDesc(FundFlow::getCreatedAt));
        return Result.ok(result);
    }

    /** 提现申请。 */
    @PostMapping("/withdraw")
    public Result<Long> withdraw(@CurrentUser LoginUser loginUser,
                                 @RequestParam Long amount,
                                 @RequestParam String account) {
        return Result.ok(withdrawalService.apply(loginUser.getUserId(), amount, account).getId());
    }
}
