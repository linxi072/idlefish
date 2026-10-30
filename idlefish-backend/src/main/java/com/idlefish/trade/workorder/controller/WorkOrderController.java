package com.idlefish.trade.workorder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.workorder.dto.CreateFromDisputeDTO;
import com.idlefish.trade.workorder.dto.CreateWorkOrderDTO;
import com.idlefish.trade.workorder.dto.RateWorkOrderDTO;
import com.idlefish.trade.workorder.dto.WorkOrderLinkedDisputeVO;
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
 * 客服工单用户端接口（F-12，需登录）：提交 / 维权转人工 / 我的工单 / 详情 / 关联维权 / 关闭（含满意度回访）。
 */
@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /** 提交工单（用户）。 */
    @PostMapping("/create")
    public Result<Long> create(@CurrentUser LoginUser user,
                               @Valid @RequestBody CreateWorkOrderDTO dto) {
        Long id = workOrderService.create(user.getUserId(), dto.getType(), dto.getPriority(),
                dto.getChannel(), dto.getSubject(), dto.getDescription(), dto.getOrderNo());
        return Result.ok(id);
    }

    /** 维权单转人工：由维权单号派生客服工单（类型/优先级/标题由维权单推导，不可伪造）。 */
    @PostMapping("/from-dispute")
    public Result<Long> createFromDispute(@CurrentUser LoginUser user,
                                          @Valid @RequestBody CreateFromDisputeDTO dto) {
        Long id = workOrderService.createFromDispute(user.getUserId(), dto.getDisputeNo(),
                dto.getChannel(), dto.getDescription());
        return Result.ok(id);
    }

    /** 我的工单（分页）。 */
    @GetMapping("/my")
    public Result<IPage<WorkOrder>> my(@CurrentUser LoginUser user,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(workOrderService.myList(user.getUserId(), page, size));
    }

    /** 工单详情（仅发起人可见）。 */
    @GetMapping("/detail")
    public Result<WorkOrder> detail(@CurrentUser LoginUser user, @RequestParam Long id) {
        return Result.ok(workOrderService.userDetail(user.getUserId(), id));
    }

    /** 工单关联的维权纠纷简要（仅发起人可见；未关联时返回空对象）。 */
    @GetMapping("/dispute")
    public Result<WorkOrderLinkedDisputeVO> linkedDispute(@CurrentUser LoginUser user, @RequestParam Long id) {
        return Result.ok(workOrderService.userLinkedDispute(user.getUserId(), id));
    }

    /** 关闭工单并满意度回访（仅发起人，必须回填 1-5 满意度）。 */
    @PostMapping("/close")
    public Result<Void> close(@CurrentUser LoginUser user,
                              @RequestParam Long id,
                              @Valid @RequestBody RateWorkOrderDTO dto) {
        workOrderService.closeByUser(user.getUserId(), id, dto.getSatisfaction());
        return Result.ok();
    }
}
