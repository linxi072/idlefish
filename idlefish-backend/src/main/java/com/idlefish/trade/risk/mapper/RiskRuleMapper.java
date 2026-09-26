package com.idlefish.trade.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.risk.entity.RiskRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可配置风控规则 Mapper（F-15.1）。
 */
@Mapper
public interface RiskRuleMapper extends BaseMapper<RiskRule> {
}
