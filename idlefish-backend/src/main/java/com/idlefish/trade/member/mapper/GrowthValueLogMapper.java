package com.idlefish.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.member.entity.GrowthValueLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 成长值流水 Mapper（ITER-F07 / REQ-09）。
 */
@Mapper
public interface GrowthValueLogMapper extends BaseMapper<GrowthValueLog> {

    /**
     * 按 (用户, 来源, 关联单号) 统计扣减类流水的绝对值之和（申诉回补时反查判责扣减量）。
     * {@code DEDUCT_DISPUTE} / {@code DEDUCT_REVERSE} 等负向流水 delta 为负，ABS 求和即累计扣减幅度。
     */
    @Select("SELECT COALESCE(SUM(ABS(delta)), 0) FROM t_growth_value_log " +
            "WHERE user_id = #{userId} AND biz_type = #{bizType} AND biz_id = #{bizId}")
    long sumAbsDeltaByBiz(@Param("userId") Long userId,
                          @Param("bizType") String bizType,
                          @Param("bizId") String bizId);

    /** 成长值流水全部 delta 之和（对账基准：应等于账户存量总成长值）。 */
    @Select("SELECT COALESCE(SUM(delta), 0) FROM t_growth_value_log")
    long sumTotalDelta();

    /** 成长值流水正向（获得类）delta 之和。 */
    @Select("SELECT COALESCE(SUM(delta), 0) FROM t_growth_value_log WHERE delta > 0")
    long sumPositiveDelta();

    /** 按来源统计流水绝对值之和（衰减 / 判责扣减 / 冲正 / 回补等分类汇总）。 */
    @Select("SELECT COALESCE(SUM(ABS(delta)), 0) FROM t_growth_value_log WHERE biz_type = #{bizType}")
    long sumAbsByBizType(@Param("bizType") String bizType);
}
