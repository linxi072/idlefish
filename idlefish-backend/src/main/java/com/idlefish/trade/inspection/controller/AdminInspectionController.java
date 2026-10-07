package com.idlefish.trade.inspection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.inspection.dto.InspectionResultDTO;
import com.idlefish.trade.inspection.entity.InspectionOrder;
import com.idlefish.trade.inspection.service.InspectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴定验货运营后台（F-02）：验货单分页 / 详情 / 机构收件 / 开始验货 / 回传结果 / 异常处置。
 *
 * <p>资金安全红线：本后台<b>仅</b>推进验货状态与落库报告，退款由既有退款链路执行，
 * 异常处置只能「释放（CANCELED）」以解冻发货，<b>绝不可</b>将异常误判为通过。
 */
@RestController
@RequestMapping("/api/admin/inspection")
public class AdminInspectionController {

    private final InspectionService inspectionService;

    public AdminInspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /** 验货单分页（status 可选；keyword 匹配验货单号/订单号）。 */
    @GetMapping("/list")
    public Result<IPage<InspectionOrder>> list(@CurrentAdmin AdminUser admin,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(inspectionService.adminPage(status, keyword, page, size));
    }

    /** 验货单详情。 */
    @GetMapping("/detail")
    public Result<InspectionOrder> detail(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        return Result.ok(inspectionService.adminDetail(id));
    }

    /** 机构收件（WAIT_PICKUP → IN_TRANSIT）。 */
    @PostMapping("/receive")
    public Result<Void> receive(@CurrentAdmin AdminUser admin,
                               @RequestParam Long id,
                               @RequestParam(required = false) Long agencyId) {
        inspectionService.receiveItem(id, agencyId);
        return Result.ok();
    }

    /** 机构开始验货（IN_TRANSIT → INSPECTING）。 */
    @PostMapping("/start")
    public Result<Void> start(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        inspectionService.startInspect(id);
        return Result.ok();
    }

    /** 机构回传结果（INSPECTING → PASSED/REJECTED，并落库不可篡改报告）。 */
    @PostMapping("/result")
    public Result<Void> result(@CurrentAdmin AdminUser admin,
                              @RequestParam Long id,
                              @RequestBody InspectionResultDTO dto) {
        inspectionService.receiveResult(id, dto);
        return Result.ok();
    }

    /** 验货异常处理（EXCEPTION → CANCELED，释放并解冻发货）。 */
    @PostMapping("/resolve-exception")
    public Result<Void> resolveException(@CurrentAdmin AdminUser admin,
                                        @RequestParam Long id,
                                        @RequestParam(required = false) String remark) {
        inspectionService.adminResolveException(id, remark);
        return Result.ok();
    }
}
