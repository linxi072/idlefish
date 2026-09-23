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
import com.idlefish.trade.risk.service.TrackService;
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

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderService(OrderMapper orderMapper, PayOrderMapper payOrderMapper,
                        ItemService itemService, ItemMapper itemMapper,
                        AddressService addressService, LogisticService logisticService,
                        SettlementService settlementService, TrackService trackService, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.payOrderMapper = payOrderMapper;
        this.itemService = itemService;
        this.itemMapper = itemMapper;
        this.addressService = addressService;
        this.logisticService = logisticService;
        this.settlementService = settlementService;
        this.trackService = trackService;
        this.objectMapper = objectMapper;
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

        // D1 埋点：下单事件（失败不影响主流程）
        try {
            trackService.track(buyerId, "order_create", orderNo, null);
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
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.SHIPPING.getCode());
        upd.setLogisticsNo(logisticsNo != null && !logisticsNo.isBlank()
                ? logisticsNo : logisticService.createLogistics(orderNo));
        if (orderMapper.updateById(upd) == 0) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
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
    }

    /** 后台代发货（运营操作，跳过卖家归属校验，仍需状态机校验 + 乐观锁）。 */
    public void adminShip(String orderNo, String logisticsNo, Long operatorId) {
        Order o = getByOrderNo(orderNo);
        if (!OrderStatus.PAID.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已支付订单可发货");
        }
        Order upd = new Order();
        upd.setId(o.getId());
        upd.setVersion(o.getVersion());
        upd.setStatus(OrderStatus.SHIPPING.getCode());
        upd.setLogisticsNo(logisticsNo != null && !logisticsNo.isBlank()
                ? logisticsNo : logisticService.createLogistics(orderNo));
        if (orderMapper.updateById(upd) == 0) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
        }
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
            setClosed(o, "timeout");
            closePay(o.getPayNo());
            itemService.releaseStock(o.getItemId(), o.getQuantity());
        }
    }

    /** 定时：运输中订单超过 10 天未确认收货，自动确认并完成结算。 */
    public void autoConfirmReceive() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(10);
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.SHIPPING.getCode())
                .lt(Order::getCreatedAt, threshold));
        for (Order o : list) {
            Order upd = new Order();
            upd.setId(o.getId());
            upd.setStatus(OrderStatus.COMPLETED.getCode());
            orderMapper.updateById(upd);
            itemService.markSold(o.getItemId());
            settlementService.onTradeSuccess(o.getOrderNo());
        }
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
