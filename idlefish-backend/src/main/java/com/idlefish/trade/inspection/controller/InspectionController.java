package com.idlefish.trade.inspection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.inspection.entity.InspectionOrder;
import com.idlefish.trade.inspection.service.InspectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴定验货用户端接口（F-02，需登录）：送检 / 取消 / 我的验货单 / 详情 / 报告调阅。
 *
 * <p>REQ-01 验货为可选增值服务，不阻塞下单；本接口仅在已支付订单上创建验货单。
 */
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /** 送检创建验货单（REQ-02）。type=STANDARD 标准 / ACCURATE 精密。 */
    @PostMapping("/create")
    public Result<Long> create(@CurrentUser LoginUser user,
                              @RequestParam String orderNo,
                              @RequestParam String type,
                              @RequestParam(required = false) Long agencyId) {
        return Result.ok(inspectionService.create(user.getUserId(), orderNo, type, agencyId));
    }

    /** 买家取消（WAIT_PICKUP/IN_TRANSIT）。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser user, @RequestParam Long id) {
        inspectionService.cancel(id, user.getUserId());
        return Result.ok();
    }

    /** 我的验货单（分页）。 */
    @GetMapping("/my")
    public Result<IPage<InspectionOrder>> my(@CurrentUser LoginUser user,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(inspectionService.myList(user.getUserId(), page, size));
    }

    /** 验货单详情（买卖双方可见）。 */
    @GetMapping("/detail")
    public Result<InspectionOrder> detail(@CurrentUser LoginUser user, @RequestParam Long id) {
        return Result.ok(inspectionService.detail(id, user.getUserId()));
    }
}
