package com.idlefish.trade.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.workorder.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客服工单映射器（F-12）。
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
