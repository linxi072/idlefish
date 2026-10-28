package com.idlefish.trade.local.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.local.dto.RecycleQuoteRequest;
import jakarta.validation.Valid;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.entity.RecycleOrder;
import com.idlefish.trade.local.service.LocalDeliveryService;
import com.idlefish.trade.local.service.RecycleService;
import com.idlefish.trade.local.vo.LocalDeliveryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同城回收与本地履约运营后台（ITER-F05）：回收单揽收 / 质检调价 / 放款 / 不可用处置，
 * 以及同城配送单的查询与状态推进。
 *
 * <p>资金安全红线（沿用 F-17 / F-02 后台纪律）：
 * <ul>
 *   <li>放款仅能对「用户已确认报价」的单执行（REQ-04/REQ-07），且系统不做自动放款；</li>
 *   <li>质检报价必须服务端二次封顶（见 {@code RecycleQuoteCalculator.capFinalAmount}），
 *       运营侧录入不改变金额上限规则；</li>
 *   <li>区域机构数据隔离（REQ-08）由 {@code orgId} 收敛；当前组织-账号绑定由 ITER-T05 承接。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/local")
public class AdminLocalController {

    private final RecycleService recycleService;
    private final LocalDeliveryService localDeliveryService;

    public AdminLocalController(RecycleService recycleService, LocalDeliveryService localDeliveryService) {
        this.recycleService = recycleService;
        this.localDeliveryService = localDeliveryService;
    }

    // ===== 回收单 =====

    /** 回收单分页（REQ-08：非超管按管理员所属机构收敛，超管可跨机构查询）。 */
    @GetMapping("/recycle/list")
    public Result<IPage<RecycleOrder>> recycleList(@CurrentAdmin AdminUser admin,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long orgId,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size) {
        // REQ-08 区域机构数据隔离：非超管强制收敛到自身机构，orgId 入参仅对超管有效
        Long scope = admin == null || "SUPER".equalsIgnoreCase(admin.getRole())
                ? orgId : admin.getOrgId();
        return Result.ok(recycleService.adminList(page, size, status, keyword, scope));
    }

    /** 回收单详情。 */
    @GetMapping("/recycle/detail")
    public Result<RecycleOrder> recycleDetail(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        return Result.ok(recycleService.adminDetail(id));
    }

    /** 揽收（PENDING_PICKUP → PICKED_UP）。 */
    @PostMapping("/recycle/pickup")
    public Result<Void> recyclePickup(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        recycleService.markPickedUp(id, admin.getUsername());
        return Result.ok();
    }

    /** 开始质检（PICKED_UP → INSPECTING）。 */
    @PostMapping("/recycle/inspecting")
    public Result<Void> recycleInspecting(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        recycleService.markInspecting(id, admin.getUsername());
        return Result.ok();
    }

    /** REQ-04 质检调价：录入最终报价并进入待用户确认（服务端二次封顶）。 */
    @PostMapping("/recycle/quote")
    public Result<Void> recycleQuote(@CurrentAdmin AdminUser admin,
                                     @RequestParam Long id,
                                     @Valid @RequestBody RecycleQuoteRequest req) {
        recycleService.quote(id, req.getFinalAmount(), req.getQuoteRemark(), admin.getUsername());
        return Result.ok();
    }

    /** REQ-07 放款：仅已确认报价的单可放款，入 RECYCLE 资金流水供 ITER-T02 对账。 */
    @PostMapping("/recycle/pay")
    public Result<Void> recyclePay(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        recycleService.pay(id, admin.getUsername());
        return Result.ok();
    }

    /** REQ-09 明确不可用：城市未开通/服务商异常时置为 UNAVAILABLE 并告知用户。 */
    @PostMapping("/recycle/unavailable")
    public Result<Void> recycleUnavailable(@CurrentAdmin AdminUser admin,
                                           @RequestParam Long id,
                                           @RequestParam(required = false) String reason) {
        recycleService.markUnavailable(id, reason);
        return Result.ok();
    }

    // ===== 同城配送单 =====

    /** 配送单分页。 */
    @GetMapping("/delivery/list")
    public Result<IPage<LocalDelivery>> deliveryList(@CurrentAdmin AdminUser admin,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String bizNo,
                                                     @RequestParam(required = false) String city,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(localDeliveryService.adminList(page, size, status, bizNo, city));
    }

    /** 配送单详情（含轨迹）。 */
    @GetMapping("/delivery/detail")
    public Result<LocalDeliveryVO> deliveryDetail(@CurrentAdmin AdminUser admin,
                                                  @RequestParam String deliveryNo) {
        return Result.ok(localDeliveryService.adminDetail(deliveryNo));
    }

    /** 配送状态推进（服务商回调或运营补录：DELIVERING / ARRIVED / SIGNED）。 */
    @PostMapping("/delivery/advance")
    public Result<Void> deliveryAdvance(@CurrentAdmin AdminUser admin,
                                        @RequestParam String deliveryNo,
                                        @RequestParam String status) {
        switch (status == null ? "" : status.toUpperCase()) {
            case "DELIVERING" -> localDeliveryService.onDelivering(deliveryNo);
            case "ARRIVED" -> localDeliveryService.onArrived(deliveryNo);
            case "SIGNED" -> localDeliveryService.sign(deliveryNo);
            default -> throw new com.idlefish.trade.common.BizException(
                    com.idlefish.trade.common.Code.PARAM_INVALID,
                    "status 仅支持 DELIVERING / ARRIVED / SIGNED");
        }
        return Result.ok();
    }
}
