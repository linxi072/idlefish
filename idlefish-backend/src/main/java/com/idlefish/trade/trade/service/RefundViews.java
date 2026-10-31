package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.vo.RefundVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 退款视图映射（纯函数）：实体 → VO，无副作用、无 Mapper 依赖。
 * 金额单位「分」原样透传；createdAt 按 {@link DateTimeUtil#FMT} 格式化为字符串（null 安全）。
 */
public final class RefundViews {

    private RefundViews() {
    }

    public static RefundVO toVO(Refund r) {
        if (r == null) {
            return null;
        }
        RefundVO vo = new RefundVO();
        vo.setRefundNo(r.getRefundNo());
        vo.setOrderNo(r.getOrderNo());
        vo.setBuyerId(r.getBuyerId());
        vo.setSellerId(r.getSellerId());
        vo.setType(r.getType());
        vo.setAmount(r.getAmount());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setLogisticsNo(r.getLogisticsNo());
        vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(DateTimeUtil.FMT));
        return vo;
    }

    public static List<RefundVO> toVOList(List<Refund> list) {
        List<RefundVO> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (Refund r : list) {
            out.add(toVO(r));
        }
        return out;
    }
}
