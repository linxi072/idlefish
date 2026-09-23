package com.idlefish.trade.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.trade.entity.DelayTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 延时任务 Mapper。
 */
@Mapper
public interface DelayTaskMapper extends BaseMapper<DelayTask> {
}
