package com.idlefish.trade.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.trade.entity.FundFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlow> {

    /**
     * 扫描 DB 级重复资金流水（T01 可观测性守卫，REQ-07 防御网）。
     * 同一 {@code biz_no} 出现多次（{@code COUNT > 1}）代表疑似重复出款/入账，
     * 由 {@code ObservabilityGuardService} 转化为 {@code fund.flow.duplicate} 计数信号。
     * <p>注：t_fund_flow 已对 biz_no 施加唯一约束拦截主路径重复；本查询为跨进程/历史异常的防御性兜底网。</p>
     */
    @Select("SELECT biz_no FROM t_fund_flow GROUP BY biz_no HAVING COUNT(*) > 1")
    List<String> listDuplicateBizNos();
}
