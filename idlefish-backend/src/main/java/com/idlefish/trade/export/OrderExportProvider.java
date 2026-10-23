package com.idlefish.trade.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.OrderMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 订单导出（ITER-T06）：无 {@code org_id} 列，仅「不受限（ALL）」管理员可导出（REQ-07 防跨机构泄露）。
 * 金额单位为「分」，保持与业务契约一致。
 */
@Component
public class OrderExportProvider implements ExportProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderMapper orderMapper;

    public OrderExportProvider(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public ExportType type() {
        return ExportType.ORDER;
    }

    @Override
    public boolean hasOrgScope() {
        return false;
    }

    @Override
    public List<String> headers() {
        return Arrays.asList("orderNo", "buyerId", "sellerId", "itemId", "quantity",
                "unitPrice", "totalAmount", "freight", "payAmount", "status",
                "deliveryType", "createdAt");
    }

    @Override
    public List<List<String>> rows(ExportContext ctx) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        w.last("LIMIT " + ctx.getLimit());
        List<Order> list = orderMapper.selectList(w);
        List<List<String>> rows = new ArrayList<>(list.size());
        for (Order o : list) {
            rows.add(Arrays.asList(
                    str(o.getOrderNo()),
                    str(o.getBuyerId()),
                    str(o.getSellerId()),
                    str(o.getItemId()),
                    str(o.getQuantity()),
                    str(o.getUnitPrice()),
                    str(o.getTotalAmount()),
                    str(o.getFreight()),
                    str(o.getPayAmount()),
                    str(o.getStatus()),
                    str(o.getDeliveryType()),
                    fmt(o.getCreatedAt())));
        }
        return rows;
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }
}
