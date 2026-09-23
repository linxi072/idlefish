package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟物流（idlefish.logistics.mock=true 默认）：生成单号、模拟轨迹并落库 t_logistics。
 */
@Service
@ConditionalOnProperty(name = "idlefish.logistics.mock", havingValue = "true", matchIfMissing = true)
public class MockLogisticsServiceImpl implements LogisticService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogisticsMapper logisticsMapper;
    private final ObjectMapper objectMapper;

    public MockLogisticsServiceImpl(LogisticsMapper logisticsMapper, ObjectMapper objectMapper) {
        this.logisticsMapper = logisticsMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String createLogistics(String orderNo) {
        return "SF" + System.nanoTime();
    }

    @Override
    public void persistShip(String orderNo, String logisticsNo, String company) {
        Logistics l = logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>()
                .eq(Logistics::getOrderNo, orderNo));
        List<Map<String, String>> init = List.of(of(LocalDateTime.now(), "【" + logisticsNo + "】已揽收"));
        if (l == null) {
            l = new Logistics();
            l.setOrderNo(orderNo);
            l.setLogisticsNo(logisticsNo);
            l.setCompany(company);
            l.setStatus("transport");
            l.setDetailJson(toJson(init));
            logisticsMapper.insert(l);
        } else {
            l.setLogisticsNo(logisticsNo);
            l.setCompany(company);
            l.setStatus("transport");
            l.setDetailJson(toJson(init));
            logisticsMapper.updateById(l);
        }
    }

    @Override
    public List<Map<String, String>> track(String logisticsNo) {
        if (logisticsNo == null || logisticsNo.isBlank()) {
            return List.of();
        }
        Logistics l = logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>()
                .eq(Logistics::getLogisticsNo, logisticsNo).last("LIMIT 1"));
        List<Map<String, String>> list = new ArrayList<>();
        list.add(of(LocalDateTime.now(), "【" + logisticsNo + "】已揽收"));
        list.add(of(LocalDateTime.now().plusHours(6), "运输中，已到达分拣中心"));
        list.add(of(LocalDateTime.now().plusHours(24), "已签收（模拟）"));
        if (l != null) {
            l.setDetailJson(toJson(list));
            l.setStatus("signed");
            logisticsMapper.updateById(l);
        }
        return list;
    }

    private Map<String, String> of(LocalDateTime t, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", t.format(FMT));
        m.put("desc", desc);
        return m;
    }

    private String toJson(List<Map<String, String>> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
