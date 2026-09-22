package com.idlefish.trade.item.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.ItemStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 商品状态机（PRD §4.2，七态）。
 * draft → pending_review → onsale → { locked → sold | off_shelf | rejected }，off_shelf 可重新上架。
 */
@Component
public class ItemStateMachine {

    private static final Map<ItemStatus, Set<ItemStatus>> TRANSITIONS = new EnumMap<>(ItemStatus.class);

    static {
        TRANSITIONS.put(ItemStatus.DRAFT, EnumSet.of(ItemStatus.PENDING_REVIEW, ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.PENDING_REVIEW, EnumSet.of(ItemStatus.ONSALE, ItemStatus.REJECTED, ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.ONSALE, EnumSet.of(ItemStatus.LOCKED, ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.LOCKED, EnumSet.of(ItemStatus.ONSALE, ItemStatus.SOLD, ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.SOLD, EnumSet.of(ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.REJECTED, EnumSet.of(ItemStatus.OFF_SHELF));
        TRANSITIONS.put(ItemStatus.OFF_SHELF, EnumSet.of(ItemStatus.ONSALE));
    }

    /** 校验状态迁移是否合法，非法则抛 STATE_NOT_ALLOWED。 */
    public void validate(ItemStatus from, ItemStatus to) {
        if (from == to) {
            return;
        }
        Set<ItemStatus> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BizException(Code.STATE_NOT_ALLOWED,
                    String.format("商品状态 %s 不允许流转到 %s", from.getDesc(), to.getDesc()));
        }
    }
}
