package com.idlefish.trade.trade.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流服务（Mock）：生成物流单号与模拟轨迹。
 */
@Service
public class LogisticService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 下单发货时生成物流单号。 */
    public String createLogistics(String orderNo) {
        return "SF" + System.nanoTime();
    }

    /** 模拟轨迹查询。 */
    public List<Map<String, String>> track(String logisticsNo) {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(of(LocalDateTime.now(), "【" + logisticsNo + "】已揽收"));
        list.add(of(LocalDateTime.now().plusHours(6), "运输中，已到达分拣中心"));
        list.add(of(LocalDateTime.now().plusHours(24), "已签收（模拟）"));
        return list;
    }

    private Map<String, String> of(LocalDateTime t, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", t.format(FMT));
        m.put("desc", desc);
        return m;
    }
}
