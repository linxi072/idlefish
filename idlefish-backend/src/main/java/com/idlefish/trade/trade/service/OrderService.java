package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.trade.dto.OrderCreateDTO;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.vo.OrderCreateVO;
import com.idlefish.trade.trade.vo.OrderItemVO;
import com.idlefish.trade.trade.vo.OrderVO;
import com.idlefish.trade.user.service.AddressService;
import com.idlefish.trade.user.service.CreditService;
import com.idlefish.trade.risk.service.TrackService;
import com.idlefish.trade.trade.service.DelayQueueService;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务：幂等下单、取消、发货、确认收货、超时关单。
 */
@Service
public class OrderService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final PayOrderMapper payOrderMapper;
    private final ItemService itemService;
    private final ItemMapper itemMapper;
    private final AddressService addressService;
    private final LogisticService logisticService;
    private final SettlementService settlementService;
    private final TrackService trackService;
    private final ObjectMapper objectMapper;
    private final DelayQueueService delayQueueService;
    private final NotificationService notificationService;
    private final CreditService creditService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderService(OrderMapper orderMapper, PayOrderMapper payOrderMapper,
                        ItemService itemService, ItemMapper itemMapper,
                        AddressService addressService, LogisticService logisticService,
                        SettlementService settlementService, TrackService trackService, ObjectMapper objectMapper,
                        @Lazy DelayQueueService delayQueueService, NotificationService notificationService,
                        CreditService creditService) {
        this.orderMapper = orderMapper;
        this.payOrderMapper = payOrderMapper;
        this.itemService = itemService;
        this.itemMapper = itemMapper;
        this.addressService = addressService;
        this.logisticService = logisticService;
        this.settlementService = settlementService;
        this.trackService = trackService;
        this.objectMapper = objectMapper;
        this.delayQueueService = delayQueueService;
        this.notificationService = notificationService;
        this.creditService = creditService;
    }

    /** 创建订单（幂等：同买家同商品存在待支付订单则直接返回）。 */
    public OrderCreateVO createOrder(Long buyerId, OrderCreateDTO dto) {
        Long itemId = dto.getItemId();
        int qty = dto.getQuantity() == null ? 1 : dto.getQuantity();

        Order exist = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getBuyerId, buyerId)
                .eq(Order::getItemId, itemId)
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.getCode()));
        if (exist != null) {
            OrderCreateVO vo = new OrderCreateVO();
            vo.setOrderNo(exist.getOrderNo());
            vo.setAmount(exist.getPayAmount());
            return vo;
        }

        itemService.lockStock(itemId, qty); // 校验在售 + 扣库存

        Item item = itemMapper.selectById(itemId);
        Long unitPrice = item.getPrice();
        Long freight = item.getFreight() == null ? 0L : item.getFreight();
        Long total = unitPrice * qty;
        Long payAmount = total + freight;

        String orderNo = IdGenerator.orderNo();
        String payNo = IdGenerator.payNo();

        Order o = new Order();
        o.setOrderNo(orderNo);
        o.setBuyerId(buyerId);
        o.setSellerId(item.getSellerId());
        o.setItemId(itemId);
        o.setSkuSnapshot(itemService.itemSnapshot(itemId));
        o.setQuantity(qty);
        o.setUnitPrice(unitPrice);
        o.setTotalAmount(total);
        o.setFreight(freight);
        o.setPayAmount(payAmount);
        o.setStatus(OrderStatus.PENDING_PAY.getCode());
        o.setAddressSnapshot(addressService.snapshot(dto.getAddressId()));
        o.setRemark(dto.getRemark());
        o.setPayNo(payNo);
        orderMapper.insert(o);

        PayOrder po = new PayOrder();
        po.setPayNo(payNo);
        po.setOrderNo(orderNo);
        po.setBuyerId(buyerId);
        po.setAmount(payAmount);
        po.setChannel("wechat");
        po.setStatus(PayStatus.WAIT.getCode());
        payOrderMapper.insert(po);

        // D6 延时队列：下单后提交 30 分钟关单延时任务（精准到点触发；本地实现或 RocketMQ 均走此路径）
        try {
            delayQueueService.submit("ORDER_CLOSE", orderNo, "", 30 * 60);
        } catch (Exception ignore) {
            // 延时任务提交失败不影响下单主流程（定时扫描兜底关单）
        }

        // D1 埋点：下单事件（携带金额，供设备维度风控 R3 判定；失败不影响主流程）
        try {
            trackService.track(buyerId, "order_create", orderNo, "{\"amount\":" + payAmount + "}");
        } catch (Exception ignore) {
            // 埋点异常忽略，避免影响下单
        }

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(orderNo);
        vo.setAmount(payAmount);
        return vo;
    }

    /** 取消订单（仅待支付），释放库存。 */
    public void cancelOrder(Long buyerId, String orderNo) {
        Order o = ownedBuyer(buyerId, orderNo);
        if (!OrderStatus.PENDING_PAY.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅待支付订单可取消");
        }
        setClosed(o, "cancel");
        closePay(o.getPayNo());
        itemService.releaseStock(o.getItemId(), o.getQuantity());
    }

    /** 卖家发货。 */
    public void ship(Long sellerId, String orderNo, String logisticsNo) {
        Order o = ownedSeller(sellerId, orderNo);
        if (!OrderStatus.PAID.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已支付订单可发货");
        }
        String no = logisticsNo != null && !logisticsNo.isBlank()
                ? logisticsNo : logisticService.createLogistics(orderNo);
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.SHIPPING.getCode());
        upd.setLogisticsNo(no);
        if (orderMapper.updateById(upd) == 0) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
        }
        logisticService.persistShip(orderNo, no, "SF");

        // F-02 通知中心：发货触达买家（best-effort）
        notificationService.notify(o.getBuyerId(), NotificationType.ORDER_SHIPPED, orderNo, "卖家已发货",
                "订单 " + orderNo + " 已发货，物流单号 " + no);

        // D6 延时队列：发货后提交 10 天自动确认收货延时任务（精准到点触发；本地/RocketMQ 均走此路径）
        try {
            delayQueueService.submit("ORDER_CONFIRM", orderNo, "", 10 * 24 * 60 * 60);
        } catch (Exception ignore) {
            // 延时任务提交失败不影响发货主流程（定时扫描兜底确认）
        }
    }

    /** 买家确认收货：完成交易并触发结算。 */
    public void confirmReceive(Long buyerId, String orderNo) {
        Order o = ownedBuyer(buyerId, orderNo);
        if (!OrderStatus.SHIPPING.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅运输中订单可确认收货");
        }
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.COMPLETED.getCode());
        if (orderMapper.updateById(upd) == 0) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
        }
        itemService.markSold(o.getItemId());
        settlementService.onTradeSuccess(orderNo);

        // F-02 通知中心：确认收货触达卖家（best-effort）
        notificationService.notify(o.getSellerId(), NotificationType.ORDER_CONFIRMED, orderNo, "买家已确认收货",
                "订单 " + orderNo + " 已确认收货，款项已结算");
        // F-05 闭环：同步触达买家（订单已完成）
        notificationService.notify(o.getBuyerId(), NotificationType.ORDER_CONFIRMED, orderNo, "订单已完成",
                "订单 " + orderNo + " 已确认收货，款项已结算");
        // F-06 信用：交易完成，重算买卖家信用分
        creditService.recompute(o.getBuyerId());
        creditService.recompute(o.getSellerId());
    }
    public void adminShip(String orderNo, String logisticsNo, Long operatorId) {
        Order o = getByOrderNo(orderNo);
        if (!OrderStatus.PAID.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已支付订单可发货");
        }
        String no = logisticsNo != null && !logisticsNo.isBlank()
                ? logisticsNo : logisticService.createLogistics(orderNo);
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.SHIPPING.getCode());
        upd.setLogisticsNo(no);
        if (orderMapper.updateById(upd) == 0) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
        }
        logisticService.persistShip(orderNo, no, "SF");
    }

    /** 物流轨迹查询（买家/卖家均可，按订单号取物流单号）。 */
    public java.util.List<java.util.Map<String, String>> logisticsTrack(String orderNo) {
        Order o = getByOrderNo(orderNo);
        if (o.getLogisticsNo() == null || o.getLogisticsNo().isBlank()) {
            return java.util.List.of();
        }
        return logisticService.track(o.getLogisticsNo());
    }

    private Order getByOrderNo(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        return o;
    }

    public OrderVO detail(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        PayOrder po = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, o.getPayNo()));
        return toVO(o, po);
    }

    public List<OrderVO> list(Long userId, String role, String status) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if ("seller".equals(role)) {
            w.eq(Order::getSellerId, userId);
        } else {
            w.eq(Order::getBuyerId, userId);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Order::getStatus, status);
        }
        w.orderByDesc(Order::getCreatedAt);
        return orderMapper.selectList(w).stream().map(o -> {
            PayOrder po = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, o.getPayNo()));
            return toVO(o, po);
        }).collect(Collectors.toList());
    }

    /** 定时：关闭 30 分钟未支付订单并释放库存。 */
    public void closeExpiredOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.getCode())
                .lt(Order::getCreatedAt, threshold));
        for (Order o : expired) {
            closeExpiredSingle(o.getOrderNo());
        }
    }

    /** 单笔关单（供延时队列处理器调用，幂等）。 */
    public void closeExpiredSingle(String orderNo) {
        Order o = getByOrderNo(orderNo);
        if (!OrderStatus.PENDING_PAY.getCode().equals(o.getStatus())) {
            return;
        }
        setClosed(o, "timeout");
        closePay(o.getPayNo());
        itemService.releaseStock(o.getItemId(), o.getQuantity());

        // F-02 通知中心：超时关单触达买家（best-effort）
        notificationService.notify(o.getBuyerId(), NotificationType.ORDER_CLOSED, orderNo, "订单已关闭",
                "订单 " + orderNo + " 超时未支付，已自动关闭");
    }

    /** 定时：运输中订单超过 10 天未确认收货，自动确认并完成结算。 */
    public void autoConfirmReceive() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(10);
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.SHIPPING.getCode())
                .lt(Order::getCreatedAt, threshold));
        for (Order o : list) {
            confirmReceiveSingle(o.getOrderNo());
        }
    }

    /** 单笔自动确认收货（供延时队列处理器调用，幂等）。 */
    public void confirmReceiveSingle(String orderNo) {
        Order o = getByOrderNo(orderNo);
        if (!OrderStatus.SHIPPING.getCode().equals(o.getStatus())) {
            return;
        }
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setStatus(OrderStatus.COMPLETED.getCode());
        orderMapper.updateById(upd);
        itemService.markSold(o.getItemId());
        settlementService.onTradeSuccess(o.getOrderNo());

        // F-02 通知中心：自动确认收货触达卖家（best-effort）
        notificationService.notify(o.getSellerId(), NotificationType.ORDER_CONFIRMED, o.getOrderNo(), "买家已确认收货",
                "订单 " + o.getOrderNo() + " 已确认收货，款项已结算");
        // F-05 闭环：同步触达买家（订单已完成）
        notificationService.notify(o.getBuyerId(), NotificationType.ORDER_CONFIRMED, o.getOrderNo(), "订单已完成",
                "订单 " + o.getOrderNo() + " 已确认收货，款项已结算");
    }

    /** 定时：已支付超过 72h 未发货，返回需提醒的订单数（提醒由通知中心消费）。 */
    public long remindUnshipped() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(72);
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .lt(Order::getCreatedAt, threshold));
        for (Order o : list) {
            log.warn("[remind] 订单 {} 已支付 72h 未发货，提醒卖家 {}", o.getOrderNo(), o.getSellerId());
        }
        return list.size();
    }

    // ---------- 内部工具 ----------

    private Double fenToYuan(Long fen) {
        return fen == null ? null : fen / 100.0;
    }

    private Order ownedBuyer(Long buyerId, String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) throw new BizException(Code.ORDER_NOT_FOUND);
        if (!o.getBuyerId().equals(buyerId)) throw new BizException(Code.STATE_NOT_ALLOWED, "无权操作该订单");
        return o;
    }

    private Order ownedSeller(Long sellerId, String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) throw new BizException(Code.ORDER_NOT_FOUND);
        if (!o.getSellerId().equals(sellerId)) throw new BizException(Code.STATE_NOT_ALLOWED, "无权操作该订单");
        return o;
    }

    private void setClosed(Order o, String closeType) {
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.CLOSED.getCode());
        upd.setCloseType(closeType);
        orderMapper.updateById(upd);
    }

    private void closePay(String payNo) {
        PayOrder po = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po != null && PayStatus.WAIT.getCode().equals(po.getStatus())) {
            PayOrder upd = new PayOrder();
            upd.setId(po.getId());
            upd.setStatus(PayStatus.CLOSED.getCode());
            payOrderMapper.updateById(upd);
        }
    }

    private OrderVO toVO(Order o, PayOrder po) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(o.getOrderNo());
        vo.setBuyerId(o.getBuyerId());
        vo.setSellerId(o.getSellerId());
        vo.setItemId(o.getItemId());
        vo.setQuantity(o.getQuantity());
        vo.setUnitPrice(o.getUnitPrice());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setFreight(o.getFreight());
        vo.setPayAmount(o.getPayAmount());
        vo.setAmount(o.getPayAmount());
        vo.setUnitPriceYuan(fenToYuan(o.getUnitPrice()));
        vo.setTotalAmountYuan(fenToYuan(o.getTotalAmount()));
        vo.setFreightYuan(fenToYuan(o.getFreight()));
        vo.setPayAmountYuan(fenToYuan(o.getPayAmount()));
        vo.setAmountYuan(fenToYuan(o.getPayAmount()));
        vo.setStatus(o.getStatus());
        vo.setRemark(o.getRemark());
        vo.setLogisticsNo(o.getLogisticsNo());
        vo.setCreatedAt(o.getCreatedAt() == null ? null : o.getCreatedAt().format(FMT));
        if (po != null) {
            vo.setPayStatus(po.getStatus());
        }
        try {
            if (o.getSkuSnapshot() != null) {
                JsonNode n = objectMapper.readTree(o.getSkuSnapshot());
                vo.setTitle(n.path("title").asText(null));
                vo.setCover(n.path("cover").asText(null));
            }
            if (o.getAddressSnapshot() != null) {
                JsonNode a = objectMapper.readTree(o.getAddressSnapshot());
                vo.setAddressReceiver(a.path("receiverName").asText(null));
                vo.setAddressPhone(a.path("phone").asText(null));
                vo.setAddressDetail(String.join(" ",
                        a.path("province").asText(""), a.path("city").asText(""),
                        a.path("district").asText(""), a.path("detail").asText("")).trim());
            }
        } catch (Exception ignored) {
            // 快照解析失败不影响主流程
        }
        // R-12：构建内嵌商品轻量视图（真实后端模式下前端依赖 o.item.id / o.item.seller.id）
        OrderItemVO itemVO = new OrderItemVO();
        itemVO.setId(o.getItemId());
        itemVO.setSellerId(o.getSellerId());
        itemVO.setTitle(vo.getTitle());
        itemVO.setCover(vo.getCover());
        OrderItemVO.SellerRef sellerRef = new OrderItemVO.SellerRef();
        sellerRef.setId(o.getSellerId());
        itemVO.setSeller(sellerRef);
        vo.setItem(itemVO);
        return vo;
    }
}
