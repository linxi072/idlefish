package com.idlefish.trade.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 商品导出（ITER-T06）：带 {@code org_id}（ITER-T05 写入），受数据范围约束。
 * 金额单位为「分」，保持与业务契约一致（导出即原始存储值，避免二次换算歧义）。
 */
@Component
public class ItemExportProvider implements ExportProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ItemMapper itemMapper;

    public ItemExportProvider(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    public ExportType type() {
        return ExportType.ITEM;
    }

    @Override
    public boolean hasOrgScope() {
        return true;
    }

    @Override
    public List<String> headers() {
        return Arrays.asList("id", "sellerId", "title", "price", "originalPrice",
                "conditionLevel", "stock", "status", "auditStatus", "orgId", "city", "createdAt");
    }

    @Override
    public List<List<String>> rows(ExportContext ctx) {
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<>();
        if (!ctx.isUnrestricted() && ctx.getOrgIds() != null && !ctx.getOrgIds().isEmpty()) {
            w.in(Item::getOrgId, ctx.getOrgIds());
        }
        w.last("LIMIT " + ctx.getLimit());
        List<Item> list = itemMapper.selectList(w);
        List<List<String>> rows = new ArrayList<>(list.size());
        for (Item it : list) {
            rows.add(Arrays.asList(
                    str(it.getId()),
                    str(it.getSellerId()),
                    str(it.getTitle()),
                    str(it.getPrice()),
                    str(it.getOriginalPrice()),
                    str(it.getConditionLevel()),
                    str(it.getStock()),
                    str(it.getStatus()),
                    str(it.getAuditStatus()),
                    str(it.getOrgId()),
                    str(it.getCity()),
                    fmt(it.getCreatedAt())));
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
