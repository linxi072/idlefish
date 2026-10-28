package com.idlefish.trade.workorder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.workorder.dto.AdminTransitionWorkOrderDTO;
import com.idlefish.trade.workorder.dto.AssignWorkOrderDTO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服工单运营后台（F-12）：工单分页 / 详情 / 分配 / 流转（处理-待补充-解决-关闭）。
 *
 * <p>资金相关裁决不在本服务内；本后台仅承担「客服流转」编排，状态变更由 {@link WorkOrderService} 统一校验。
 */
@RestController
@RequestMapping("/api/admin/workorders")
public class AdminWorkOrderController {

    private final WorkOrderService workOrderService;

    public AdminWorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /** 工单分页（status/type/priority 可选；keyword 匹配工单号/订单号/标题）。 */
    @GetMapping("/list")
    public Result<IPage<WorkOrder>> list(@CurrentAdmin AdminUser admin,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(workOrderService.adminPage(status, type, priority, keyword, page, size));
    }

    /** 工单详情。 */
    @GetMapping("/detail")
    public Result<WorkOrder> detail(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        return Result.ok(workOrderService.adminDetail(id));
    }

    /** 分配受理客服（→ IN_PROGRESS）。 */
    @PostMapping("/assign")
    public Result<Void> assign(@CurrentAdmin AdminUser admin,
                               @RequestParam Long id,
                               @Valid @RequestBody AssignWorkOrderDTO dto) {
        workOrderService.assign(admin.getId(), id, dto.getAssigneeId());
        return Result.ok();
    }

    /** 流转工单状态（→ IN_PROGRESS / WAITING_USER / RESOLVED / CLOSED）。 */
    @PostMapping("/transition")
    public Result<Void> transition(@CurrentAdmin AdminUser admin,
                                   @RequestParam Long id,
                                   @Valid @RequestBody AdminTransitionWorkOrderDTO dto) {
        workOrderService.transition(admin.getId(), id, dto.getToStatus(), dto.getResolution());
        return Result.ok();
    }
}
