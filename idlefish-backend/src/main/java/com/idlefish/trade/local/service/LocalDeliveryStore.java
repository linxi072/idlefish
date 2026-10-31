package com.idlefish.trade.local.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.mapper.LocalDeliveryMapper;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.service.LogisticsTimelineBuilder;
import com.idlefish.trade.trade.vo.LogisticsTrack;

import java.util.List;
import java.util.Map;

/**
 * 同城配送数据访问层（ITER-F05）。承载配送单与物流轨迹的读写、分页与 JSON 序列化，
 * 由 {@link LocalDeliveryService} 在构造期实例化并委托，公开 API 与构造签名不变。
 */
class LocalDeliveryStore {

    private final LocalDeliveryMapper deliveryMapper;
    private final LogisticsMapper logisticsMapper;
    private final ObjectMapper objectMapper;

    LocalDeliveryStore(LocalDeliveryMapper deliveryMapper, LogisticsMapper logisticsMapper,
                       ObjectMapper objectMapper) {
        this.deliveryMapper = deliveryMapper;
        this.logisticsMapper = logisticsMapper;
        this.objectMapper = objectMapper;
    }

    // ===== 配送单 =====

    /** 按配送单号查询，不存在抛 LOCAL_DELIVERY_NOT_FOUND。 */
    LocalDelivery selectByDeliveryNo(String deliveryNo) {
        LocalDelivery d = deliveryMapper.selectOne(new LambdaQueryWrapper<LocalDelivery>()
                .eq(LocalDelivery::getDeliveryNo, deliveryNo).last("LIMIT 1"));
        if (d == null) {
            throw new BizException(Code.LOCAL_DELIVERY_NOT_FOUND);
        }
        return d;
    }

    void insert(LocalDelivery d) {
        deliveryMapper.insert(d);
    }

    void updateById(LocalDelivery d) {
        deliveryMapper.updateById(d);
    }

    IPage<LocalDelivery> selectPageByUser(Long userId, int page, int size) {
        return deliveryMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<LocalDelivery>()
                .eq(LocalDelivery::getUserId, userId)
                .orderByDesc(LocalDelivery::getCreatedAt));
    }

    IPage<LocalDelivery> adminSelectPage(int page, int size, String status, String bizNo, String city) {
        LambdaQueryWrapper<LocalDelivery> q = new LambdaQueryWrapper<LocalDelivery>()
                .eq(status != null && !status.isBlank(), LocalDelivery::getStatus, status)
                .eq(bizNo != null && !bizNo.isBlank(), LocalDelivery::getBizNo, bizNo)
                .eq(city != null && !city.isBlank(), LocalDelivery::getCity, city)
                .orderByDesc(LocalDelivery::getCreatedAt);
        return deliveryMapper.selectPage(new Page<>(page, size), q);
    }

    // ===== 物流轨迹（复用既有 t_logistics，不新建轨迹表） =====

    Logistics selectLogisticsByOrderNo(String bizNo) {
        return logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>()
                .eq(Logistics::getOrderNo, bizNo).last("LIMIT 1"));
    }

    void insertLogistics(Logistics l) {
        logisticsMapper.insert(l);
    }

    void updateLogistics(Logistics l) {
        logisticsMapper.updateById(l);
    }

    /** 节点列表序列化（异常降级为 "[]"）。 */
    String serializeNodes(List<Map<String, String>> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** JSON 反序列化为节点列表（异常降级为空）。 */
    List<Map<String, String>> deserializeNodes(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 写入/更新既有 t_logistics。nodes 为空表示仅初始化一条「已创建」节点。
     */
    void persistOrderTrack(String bizNo, String deliveryNo, String company, String status) {
        persistOrderTrack(bizNo, deliveryNo, company, status, List.of());
    }

    void persistOrderTrack(String bizNo, String deliveryNo, String company, String status,
                           List<Map<String, String>> nodes) {
        List<Map<String, String>> detail = nodes;
        if (detail == null || detail.isEmpty()) {
            detail = List.of(LocalDeliveryViews.node(java.time.LocalDateTime.now(),
                    "【" + deliveryNo + "】同城配送已创建（" + company + "）"));
        }
        Logistics l = selectLogisticsByOrderNo(bizNo);
        String json = serializeNodes(detail);
        if (l == null) {
            l = new Logistics();
            l.setOrderNo(bizNo);
            l.setLogisticsNo(deliveryNo);
            l.setCompany(company);
            l.setStatus(LocalDeliveryViews.toLogisticsStatus(status));
            l.setDetailJson(json);
            insertLogistics(l);
        } else {
            l.setLogisticsNo(deliveryNo);
            l.setCompany(company);
            l.setStatus(LocalDeliveryViews.toLogisticsStatus(status));
            l.setDetailJson(json);
            updateLogistics(l);
        }
    }

    /** 读取订单物流轨迹并渲染为结构化时间轴（异常降级为空）。 */
    List<LogisticsTrack> readOrderTracks(String bizNo, String company) {
        Logistics l = selectLogisticsByOrderNo(bizNo);
        if (l == null || l.getDetailJson() == null || l.getDetailJson().isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> nodes = deserializeNodes(l.getDetailJson());
            if (nodes.isEmpty()) {
                return List.of();
            }
            return LogisticsTimelineBuilder.build(nodes, l.getCompany() == null ? company : l.getCompany(),
                    l.getStatus()).getTracks();
        } catch (Exception e) {
            return List.of();
        }
    }
}
