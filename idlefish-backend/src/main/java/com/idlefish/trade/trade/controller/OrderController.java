package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.dto.OrderCreateDTO;
import com.idlefish.trade.trade.service.OrderService;
import com.idlefish.trade.trade.vo.OrderVO;
import com.idlefish.trade.trade.vo.OrderCreateVO;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 订单接口（交易闭环：下单 / 取消 / 发货 / 确认收货 / 列表 / 详情）。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 创建订单（买家）。 */
    @PostMapping("/create")
    public Result<OrderCreateVO> create(@CurrentUser LoginUser loginUser, @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.createOrder(loginUser.getUserId(), dto));
    }

    /** 取消订单（买家，仅待支付）。 */
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser loginUser, @PathVariable String orderNo) {
        orderService.cancelOrder(loginUser.getUserId(), orderNo);
        return Result.ok();
    }

    /** 卖家发货。 */
    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@CurrentUser LoginUser loginUser, @PathVariable String orderNo,
                             @RequestParam(required = false) String logisticsNo) {
        orderService.ship(loginUser.getUserId(), orderNo, logisticsNo);
        return Result.ok();
    }

    /** 买家确认收货（完成交易并触发结算）。 */
    @PostMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@CurrentUser LoginUser loginUser, @PathVariable String orderNo) {
        orderService.confirmReceive(loginUser.getUserId(), orderNo);
        return Result.ok();
    }

    /** 订单详情。 */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    /** 订单列表：role=buyer|seller，status 可选。 */
    @GetMapping
    public Result<List<OrderVO>> list(@CurrentUser LoginUser loginUser,
                                      @RequestParam(defaultValue = "buyer") String role,
                                      @RequestParam(required = false) String status) {
        return Result.ok(orderService.list(loginUser.getUserId(), role, status));
    }
}
