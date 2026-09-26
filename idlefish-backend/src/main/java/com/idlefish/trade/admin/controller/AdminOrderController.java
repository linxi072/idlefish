package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.service.OrderService;
import com.idlefish.trade.trade.service.RefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理（PRD §7）。
 * 订单列表 / 详情 / 后台代发货 / 后台代退款，所有写操作经 RBAC 权限校验与审计留痕。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final RefundService refundService;

    public AdminOrderController(AdminService adminService, OrderService orderService, RefundService refundService) {
        this.adminService = adminService;
        this.orderService = orderService;
        this.refundService = refundService;
    }

    /** 订单列表，支持状态过滤与关键字（订单号/商品标题）搜索。 */
    @GetMapping("/orders")
    public Result<IPage<Order>> orders(@CurrentAdmin AdminUser admin,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.orderList(status, keyword, page, size));
    }

    /** 订单详情。 */
    @GetMapping("/orders/{orderNo}")
    public Result<Order> orderDetail(@CurrentAdmin AdminUser admin, @PathVariable String orderNo) {
        return Result.ok(adminService.orderDetail(orderNo));
    }

    /** 后台代发货（运营操作，R-13）。 */
    @PostMapping("/orders/{orderNo}/ship")
    public Result<Void> ship(@CurrentAdmin AdminUser admin, @PathVariable String orderNo,
                             @RequestParam(required = false) String logisticsNo) {
        orderService.adminShip(orderNo, logisticsNo, admin.getId());
        return Result.ok();
    }

    /** 后台代处理退款同意（运营操作，R-13）。 */
    @PostMapping("/orders/{orderNo}/refund")
    public Result<Void> refund(@CurrentAdmin AdminUser admin, @PathVariable String orderNo) {
        refundService.adminAgree(orderNo);
        return Result.ok();
    }
}
