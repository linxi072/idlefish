package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实物流（真实物流查询，默认启用）：对接快递100 实时查询 API。
 * 采用 MD5(param + customer + key) 签名；网络/解析异常时降级为模拟轨迹，保证发货链路可用。
 */
@Service
public class RealLogisticsServiceImpl implements LogisticService {

    private static final Logger log = LoggerFactory.getLogger(RealLogisticsServiceImpl.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IdlefishProperties props;
    private final LogisticsMapper logisticsMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public RealLogisticsServiceImpl(IdlefishProperties props, LogisticsMapper logisticsMapper,
                                    ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.props = props;
        this.logisticsMapper = logisticsMapper;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
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
        List<Map<String, String>> remote = queryRemote(logisticsNo, l);
        if (remote != null && !remote.isEmpty()) {
            persistDetail(l, remote);
            return remote;
        }
        return buildFallback(logisticsNo, l);
    }

    /** 调用快递100 实时查询并解析轨迹；网络/解析异常或返回异常时返回空列表（交由降级处理）。 */
    private List<Map<String, String>> queryRemote(String logisticsNo, Logistics l) {
        try {
            IdlefishProperties.Logistics cfg = props.getLogistics();
            Map<String, Object> paramMap = new LinkedHashMap<>();
            paramMap.put("com", l == null ? "sf" : (l.getCompany() == null ? "sf" : l.getCompany()));
            paramMap.put("num", logisticsNo);
            String param = objectMapper.writeValueAsString(paramMap);
            // 快递100 签名：MD5(param + customer + key) 取大写（按服务商版本调整）
            String sign = SignUtils.md5Hex(param + cfg.getCustomer() + cfg.getKey()).toUpperCase();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("customer", cfg.getCustomer());
            body.add("param", param);
            body.add("sign", sign);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String resp = restTemplate.postForObject(cfg.getUrl(), new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp);
            if (root.path("status").asText("").equals("200") || root.path("message").asText("").contains("ok")) {
                JsonNode data = root.path("data");
                List<Map<String, String>> list = new ArrayList<>();
                if (data.isArray()) {
                    for (JsonNode n : data) {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("time", n.path("ftime").asText("") + n.path("time").asText(""));
                        m.put("desc", n.path("context").asText(""));
                        list.add(m);
                    }
                }
                return list;
            }
            log.warn("物流商返回异常，降级模拟轨迹: {}", resp);
        } catch (Exception e) {
            log.warn("物流查询失败，降级模拟轨迹: {}", e.getMessage());
        }
        return List.of();
    }

    /** 持久化解析到的轨迹明细（若存在物流记录）。 */
    private void persistDetail(Logistics l, List<Map<String, String>> list) {
        if (l != null) {
            l.setDetailJson(toJson(list));
            l.setStatus("transport");
            logisticsMapper.updateById(l);
        }
    }

    /** 物流商不可达时的降级模拟轨迹。 */
    private List<Map<String, String>> buildFallback(String logisticsNo, Logistics l) {
        List<Map<String, String>> fallback = new ArrayList<>();
        fallback.add(of(LocalDateTime.now(), "【" + logisticsNo + "】运输中（物流商暂不可达，模拟轨迹）"));
        if (l != null) {
            l.setDetailJson(toJson(fallback));
            logisticsMapper.updateById(l);
        }
        return fallback;
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
