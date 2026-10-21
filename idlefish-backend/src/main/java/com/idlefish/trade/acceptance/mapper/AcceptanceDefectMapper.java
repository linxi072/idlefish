package com.idlefish.trade.acceptance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;
import org.apache.ibatis.annotations.Mapper;

/**
 * 真机验收缺陷 Mapper（ITER-F01 / REQ-09）。
 */
@Mapper
public interface AcceptanceDefectMapper extends BaseMapper<AcceptanceDefect> {
}
