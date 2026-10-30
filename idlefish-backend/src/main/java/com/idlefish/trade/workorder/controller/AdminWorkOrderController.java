package com.idlefish.trade.workorder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.workorder.dto.AdminTransitionWorkOrderDTO;
import com.idlefish.trade.workorder.dto.AssignWorkOrderDTO;
import com.idlefish.trade.workorder.dto.WorkOrderLinkedDisputeVO;
import com.idlefish.trade.workorder.dto.WorkOrderSlaStatsVO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.service.WorkOrderService;
import com.idlefish.trade.workorder.service.WorkOrderSlaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服工单运营后台（F-12）：工单分页 / 详情 / 分配 / 流转（处理-待补充-解决-关闭）
 * + 关联维权查询 + 时效 SLA 统计与手动扫描。
 *
 * <p>资金相关裁决不在本服务内；本后台仅承担「客服流转」编排，状态变更由 {@link WorkOrderService} 统一校验。
 */
@RestController
@RequestMapping("/api/admin/workorders")
public class AdminWorkOrderController {

    private final WorkOrderService workOrderService;
    private final WorkOrderSlaService workOrderSlaService;

    public AdminWorkOrderController(WorkOrderService workOrderService, WorkOrderSlaService workOrderSlaService) {
        this.workOrderService = workOrderService;
        this.workOrderSlaService = workOrderSlaService;
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

    /** 工单关联的维权纠纷简要（运营视角；未关联时返回空对象）。 */
    @GetMapping("/dispute")
    public Result<WorkOrderLinkedDisputeVO> linkedDispute(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        return Result.ok(workOrderService.adminLinkedDispute(id));
    }

    /** 时效 SLA 统计（近 N 天建单的达标率 / 超时量 / 在办超时量）。 */
    @GetMapping("/sla/stats")
    public Result<WorkOrderSlaStatsVO> slaStats(@CurrentAdmin AdminUser admin,
                                                @RequestParam(defaultValue = "7") Integer days) {
        return Result.ok(workOrderSlaService.stats(days == null ? 7 : days));
    }

    /** 手动触发一次 SLA 超时扫描（定时任务之外的人工补扫，返回本批标记数量）。 */
    @PostMapping("/sla/scan")
    public Result<Integer> slaScan(@CurrentAdmin AdminUser admin,
                                   @RequestParam(defaultValue = "200") Integer limit) {
        return Result.ok(workOrderSlaService.scanBreached(limit == null ? 200 : limit));
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
