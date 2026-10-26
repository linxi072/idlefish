package com.idlefish.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.member.entity.UserGrowth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户成长值账户 Mapper（F-13.2）。
 */
@Mapper
public interface UserGrowthMapper extends BaseMapper<UserGrowth> {

    /** 成长值账户存量总成长值（SUM growth_value），用于 BI 对账报表。 */
    @Select("SELECT COALESCE(SUM(growth_value), 0) FROM t_user_growth")
    long sumGrowthValue();
}
