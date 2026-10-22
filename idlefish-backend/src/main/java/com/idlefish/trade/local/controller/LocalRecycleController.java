package com.idlefish.trade.local.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.local.dto.RecycleApplyRequest;
import com.idlefish.trade.local.dto.RecycleQuoteRequest;
import com.idlefish.trade.local.entity.RecycleOrder;
import com.idlefish.trade.local.service.RecycleService;
import com.idlefish.trade.local.vo.RecycleEstimateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 同城回收用户端接口（ITER-F05，需登录）：
 * 预估回收价 / 提交回收申请 / 取消 / 报价确认 / 我的回收单 / 详情。
 *
 * <p>资金与合规红线：
 * <ul>
 *   <li>预估价仅参考，最终以质检报价为准（REQ-02/REQ-04），且用户拥有是否成交的决定权（REQ-05）；</li>
 *   <li>拒绝报价 = 取消退回，平台不强制成交、不扣费（REQ-05）；</li>
 *   <li>待上门阶段取消不产生任何费用（REQ-03）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/local/recycle")
public class LocalRecycleController {

    private final RecycleService recycleService;

    public LocalRecycleController(RecycleService recycleService) {
        this.recycleService = recycleService;
    }

    /** REQ-02 预估回收价（不下单）。 */
    @PostMapping("/estimate")
    public Result<RecycleEstimateVO> estimate(@CurrentUser LoginUser user,
                                              @RequestParam Long categoryId,
                                              @RequestParam(required = false) Integer conditionLevel,
                                              @RequestParam(required = false) Long referencePriceFen,
                                              @RequestParam(required = false) String city) {
        return Result.ok(recycleService.estimate(categoryId, conditionLevel, referencePriceFen, city, null));
    }

    /** REQ-02/REQ-11 提交回收申请（返回回收单 ID）。 */
    @PostMapping("/apply")
    public Result<Long> apply(@CurrentUser LoginUser user, @Valid @RequestBody RecycleApplyRequest req) {
        return Result.ok(recycleService.apply(user.getUserId(), req));
    }

    /** REQ-03 取消回收单（仅待上门阶段，无费用）。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser user,
                               @RequestParam Long id,
                               @RequestParam(required = false) String reason) {
        recycleService.cancel(user.getUserId(), id, reason);
        return Result.ok();
    }

    /** REQ-04/REQ-05 接受或拒绝质检报价（拒绝即取消退回）。 */
    @PostMapping("/respond")
    public Result<Void> respond(@CurrentUser LoginUser user,
                                @RequestParam Long id,
                                @RequestParam boolean accept) {
        recycleService.respond(user.getUserId(), id, accept);
        return Result.ok();
    }

    /** 我的回收单（分页）。 */
    @GetMapping("/my")
    public Result<IPage<RecycleOrder>> my(@CurrentUser LoginUser user,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(recycleService.myList(user.getUserId(), page, size));
    }

    /** 回收单详情（仅本人）。 */
    @GetMapping("/detail")
    public Result<RecycleOrder> detail(@CurrentUser LoginUser user, @RequestParam Long id) {
        return Result.ok(recycleService.detail(user.getUserId(), id));
    }
}
