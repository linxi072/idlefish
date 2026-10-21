package com.idlefish.trade.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 扫描超时未支付订单（T01 可观测性守卫，REQ-06 防御网）。
     * 状态为 {@code pending_pay} 且创建时间早于 cutoff 的订单，视为「应有关联支付流水却迟迟未支付」的异常卡单，
     * 由 {@code ObservabilityGuardService} 转化为 {@code pay.callback.timeout} 计数信号。
     * cutoff 通常取 30 分钟（与 {@code TradeScheduler.closeExpired} 关单窗口对齐）。
     */
    @Select("SELECT * FROM t_order WHERE status = 'pending_pay' AND created_at < #{cutoff}")
    List<Order> listUnpaidOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
