package com.idlefish.trade.local.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.common.util.MoneyUtil;
import com.idlefish.trade.local.LocalDeliveryStateMachine;
import com.idlefish.trade.local.LocalFulfillmentRules;
import com.idlefish.trade.local.dto.LocalDeliveryApplyRequest;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.mapper.LocalDeliveryMapper;
import com.idlefish.trade.local.provider.DispatchCommand;
import com.idlefish.trade.local.provider.DispatchResult;
import com.idlefish.trade.local.provider.LocalFulfillmentProvider;
import com.idlefish.trade.local.provider.TrackPoint;
import com.idlefish.trade.local.vo.LocalAvailabilityVO;
import com.idlefish.trade.local.vo.LocalDeliveryVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.service.LogisticsTimelineBuilder;
import com.idlefish.trade.trade.vo.LogisticsTrack;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同城配送服务（ITER-F05 / REQ-06、REQ-07、REQ-09）。
 *
 * <p>定位：本系统只做「下单方 + 轨迹消费方」，骑手调度完全委托第三方服务商
 * {@link LocalFulfillmentProvider}（不自建车队，符合 PRD 非目标）。
 *
 * <p>REQ-09 硬性纪律：服务商未配置 / 调度失败 / 超范围一律抛
 * {@link Code#LOCAL_DELIVERY_UNAVAILABLE} 并给出明确原因，绝不用本地假运单蒙混。
 *
 * <p>REQ-06 轨迹闭环：第三方回传节点归一化后写入既有 {@code t_logistics}（ORDER 业务），
 * 再经 {@link LogisticsTimelineBuilder} 渲染为结构化时间轴——不新建轨迹表。
 *
 * <p>REQ-07 金额：运费恒「分」，入 IN/DELIVERY_FEE 资金流水（与订单运费同一口径），
 * 可被 ITER-T02 对账按 bizNo 追平。
 */
@Service
public class LocalDeliveryService {

    private final LocalDeliveryMapper deliveryMapper;
    private final LogisticsMapper logisticsMapper;
    private final LocalFulfillmentProvider provider;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public LocalDeliveryService(LocalDeliveryMapper deliveryMapper, LogisticsMapper logisticsMapper,
                                LocalFulfillmentProvider provider, IdlefishProperties props,
                                MetricsRegistry metrics, NotificationService notificationService,
                                ObjectMapper objectMapper) {
        this.deliveryMapper = deliveryMapper;
        this.logisticsMapper = logisticsMapper;
        this.provider = provider;
        this.props = props;
        this.metrics = metrics;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /** 资金流水类型：同城配送运费（REQ-07）。 */
    public static final String DELIVERY_FEE_FLOW_TYPE = "DELIVERY_FEE";

    // ===== REQ-09 可用性预检 =====

    /**
     * 同城配送可用性预检：总开关 / 城市白名单 / 距离上限 / 服务商凭据四层判定。
     * 任一层不通过均返回 available=false + 明确原因，前端据此提示用户改用快递。
     */
    public LocalAvailabilityVO check(String city, Double distanceKm) {
        IdlefishProperties.Local cfg = props.getLocal();
        if (!cfg.isEnabled()) {
            return LocalAvailabilityVO.unavailable("同城配送服务暂未开通");
        }
        if (!LocalFulfillmentRules.isCitySupported(city, cfg.getCities())) {
            return LocalAvailabilityVO.unavailable("该城市暂未开通同城配送");
        }
        if (distanceKm != null && !LocalFulfillmentRules.isDistanceSupported(distanceKm, cfg.getDeliveryMaxKm())) {
            return LocalAvailabilityVO.unavailable("配送距离超出同城服务范围（最大 " + (long) cfg.getDeliveryMaxKm() + " 公里）");
        }
        if (!provider.configured()) {
            // 服务商未接入必须显式说明，禁止静默用本地单顶替（REQ-09）
            return LocalAvailabilityVO.unavailable("同城配送服务商暂未接入，请稍后再试或改用快递");
        }
        LocalAvailabilityVO v = new LocalAvailabilityVO();
        v.setAvailable(true);
        v.setDistanceOk(true);
        v.setProviderReady(true);
        v.setRecycleAvailable(cfg.isEnabled());
        v.setFreightFen(LocalFulfillmentRules.calcFreight(distanceKm, cfg.getDeliveryBaseFeeFen(),
                cfg.getDeliveryBaseKm(), cfg.getDeliveryPerKmFen()));
        v.setReason("");
        return v;
    }

    /** 运费预览（REQ-07，分）。 */
    public long freight(Double distanceKm) {
        IdlefishProperties.Local cfg = props.getLocal();
        return LocalFulfillmentRules.calcFreight(distanceKm, cfg.getDeliveryBaseFeeFen(),
                cfg.getDeliveryBaseKm(), cfg.getDeliveryPerKmFen());
    }

    // ===== REQ-06 创建同城配送单 =====

    /**
     * 创建同城配送单：先本地落 CREATED，再向第三方调度。
     * <p>调度失败会<b>明确置为 FAILED</b> 并抛出不可用异常（REQ-09），
     * 绝不保留一个「看起来成功、实际没有骑手」的配送单。
     */
    public LocalDelivery apply(Long userId, LocalDeliveryApplyRequest req) {
        IdlefishProperties.Local cfg = props.getLocal();
        if (!cfg.isEnabled()) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE, "同城配送服务暂未开通");
        }
        if (req.getBizType() == null || req.getBizNo() == null
                || req.getBizNo().isBlank() || req.getBizType().isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "缺少配送业务单号");
        }
        if (!LocalFulfillmentRules.isCitySupported(req.getCity(), cfg.getCities())) {
            throw new BizException(Code.LOCAL_REGION_UNSUPPORTED, "该城市暂未开通同城配送");
        }
        Double distanceKm = req.getDistanceKm();
        if (distanceKm != null && !LocalFulfillmentRules.isDistanceSupported(distanceKm, cfg.getDeliveryMaxKm())) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE,
                    "配送距离超出同城服务范围（最大 " + (long) cfg.getDeliveryMaxKm() + " 公里）");
        }
        if (!provider.configured()) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE,
                    "同城配送服务商暂未接入，请稍后再试或改用快递");
        }

        LocalDelivery d = new LocalDelivery();
        d.setDeliveryNo(IdGenerator.deliveryNo());
        d.setBizType(req.getBizType());
        d.setBizNo(req.getBizNo());
        d.setUserId(userId);
        d.setCity(req.getCity());
        d.setFromAddress(LocalFulfillmentRules.maskAddress(req.getFromAddress()));
        d.setToAddress(LocalFulfillmentRules.maskAddress(req.getToAddress()));
        d.setDistanceKm(distanceKm);
        d.setFreight(LocalFulfillmentRules.calcFreight(distanceKm, cfg.getDeliveryBaseFeeFen(),
                cfg.getDeliveryBaseKm(), cfg.getDeliveryPerKmFen()));
        d.setPaid(0);
        d.setStatus(LocalDeliveryStateMachine.CREATED);
        d.setProviderName(provider.name());
        deliveryMapper.insert(d);

        DispatchCommand cmd = new DispatchCommand();
        cmd.setDeliveryNo(d.getDeliveryNo());
        cmd.setBizType(req.getBizType());
        cmd.setBizNo(req.getBizNo());
        cmd.setCity(req.getCity());
        cmd.setFromAddress(d.getFromAddress());
        cmd.setToAddress(d.getToAddress());
        cmd.setPickupContact(LocalFulfillmentRules.maskPhone(req.getPickupContact()));
        cmd.setReceiverContact(LocalFulfillmentRules.maskPhone(req.getReceiverContact()));
        cmd.setDistanceKm(distanceKm);
        cmd.setRemark(req.getRemark());

        DispatchResult result = provider.dispatch(cmd);
        if (!result.isSuccess()) {
            // REQ-09：明确失败，落库为 FAILED 并抛出可读原因
            LocalDelivery upd = new LocalDelivery();
            upd.setId(d.getId());
            upd.setStatus(LocalDeliveryStateMachine.FAILED);
            upd.setFailReason(result.getFailReason());
            upd.setFinishedAt(LocalDateTime.now());
            deliveryMapper.updateById(upd);
            metrics.increment("local.delivery.dispatch_failed");
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE, result.getFailReason());
        }

        LocalDelivery ok = new LocalDelivery();
        ok.setId(d.getId());
        ok.setProviderNo(result.getProviderNo());
        ok.setCourierName(result.getCourierName());
        ok.setCourierPhone(LocalFulfillmentRules.maskPhone(result.getCourierPhone()));
        ok.setStatus(LocalDeliveryStateMachine.ACCEPTED);
        ok.setAcceptedAt(LocalDateTime.now());
        deliveryMapper.updateById(ok);
        // 同步内存对象，避免为回读再多一次 DB 往返
        d.setProviderNo(ok.getProviderNo());
        d.setCourierName(ok.getCourierName());
        d.setCourierPhone(ok.getCourierPhone());
        d.setStatus(ok.getStatus());
        d.setAcceptedAt(ok.getAcceptedAt());

        // REQ-06：订单同城配送写入既有 t_logistics 初始节点（前端沿用物流时间轴）
        if (isOrderBiz(req.getBizType())) {
            persistOrderTrack(req.getBizNo(), d.getDeliveryNo(), provider.name(), LocalDeliveryStateMachine.ACCEPTED);
        }

        metrics.increment("local.delivery.created");
        notifyBestEffort(userId, NotificationType.LOCAL_DELIVERY_CREATED, d.getDeliveryNo(),
                "同城配送已下单", "配送单 " + d.getDeliveryNo() + " 已创建，运费 "
                        + MoneyUtil.fenToYuan(d.getFreight()) + " 元，骑手将按时上门");
        return d;
    }

    // ===== 状态推进 =====

    /** 骑手接单（CREATED → ACCEPTED，用于异步回调补录）。 */
    public void onAccepted(String deliveryNo, String providerNo, String courierName, String courierPhone) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (!LocalDeliveryStateMachine.canTransition(d.getStatus(), LocalDeliveryStateMachine.ACCEPTED)) {
            throw new BizException(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED);
        }
        LocalDelivery upd = new LocalDelivery();
        upd.setId(d.getId());
        upd.setStatus(LocalDeliveryStateMachine.ACCEPTED);
        upd.setAcceptedAt(LocalDateTime.now());
        if (providerNo != null && !providerNo.isBlank()) {
            upd.setProviderNo(providerNo);
        }
        if (courierName != null && !courierName.isBlank()) {
            upd.setCourierName(courierName);
        }
        if (courierPhone != null && !courierPhone.isBlank()) {
            upd.setCourierPhone(LocalFulfillmentRules.maskPhone(courierPhone));
        }
        deliveryMapper.updateById(upd);
        notifyBestEffort(d.getUserId(), NotificationType.LOCAL_DELIVERY_ACCEPTED, deliveryNo,
                "骑手已接单", "配送单 " + deliveryNo + " 骑手已接单，正在赶往取货点");
    }

    /** 取货后进入配送中（ACCEPTED → DELIVERING）。 */
    public void onDelivering(String deliveryNo) {
        advance(deliveryNo, LocalDeliveryStateMachine.DELIVERING, null);
    }

    /** 送达（→ ARRIVED）。 */
    public void onArrived(String deliveryNo) {
        LocalDelivery d = advance(deliveryNo, LocalDeliveryStateMachine.ARRIVED, null);
        notifyBestEffort(d.getUserId(), NotificationType.LOCAL_DELIVERY_ARRIVED, deliveryNo,
                "配送已送达", "配送单 " + deliveryNo + " 已送达，请当面确认收货");
    }

    /** 签收（→ SIGNED，终态）：可触发运费入账。 */
    public void sign(String deliveryNo) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (!LocalDeliveryStateMachine.canTransition(d.getStatus(), LocalDeliveryStateMachine.SIGNED)) {
            throw new BizException(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED);
        }
        LocalDelivery upd = new LocalDelivery();
        upd.setId(d.getId());
        upd.setStatus(LocalDeliveryStateMachine.SIGNED);
        upd.setDeliveredAt(LocalDateTime.now());
        upd.setFinishedAt(LocalDateTime.now());
        deliveryMapper.updateById(upd);
        if (isOrderBiz(d.getBizType())) {
            persistOrderTrack(d.getBizNo(), deliveryNo, d.getProviderName(), "signed");
        }
        metrics.increment("local.delivery.signed");
    }

    /** 取消（未送达前可取消）。 */
    public void cancel(Long userId, String deliveryNo) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (userId != null && !userId.equals(d.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "无权操作该配送单");
        }
        if (!LocalDeliveryStateMachine.canCancel(d.getStatus())) {
            throw new BizException(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED);
        }
        LocalDelivery upd = new LocalDelivery();
        upd.setId(d.getId());
        upd.setStatus(LocalDeliveryStateMachine.CANCELED);
        upd.setFinishedAt(LocalDateTime.now());
        deliveryMapper.updateById(upd);
        if (d.getProviderNo() != null) {
            provider.cancel(d.getProviderNo()); // best-effort，第三方失败仍以本地取消为准
        }
        metrics.increment("local.delivery.canceled");
    }

    // ===== REQ-06 轨迹回传 =====

    /**
     * 拉取第三方轨迹并回写 {@code t_logistics}，返回结构化时间轴。
     * <p>REQ-09：服务商不可用或返回空轨迹时抛 {@code LOCAL_DELIVERY_UNAVAILABLE}，
     * 明确告知「暂无轨迹」，不返回空数组假装成功。
     */
    public LocalDeliveryVO track(String deliveryNo) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (!provider.configured()) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE, "同城配送服务商暂未接入，无法查询轨迹");
        }
        if (d.getProviderNo() == null || d.getProviderNo().isBlank()) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE, "该配送单尚未产生运单号，暂无轨迹");
        }
        List<TrackPoint> points = provider.fetchTrack(d.getProviderNo());
        if (points == null || points.isEmpty()) {
            throw new BizException(Code.LOCAL_DELIVERY_UNAVAILABLE, "暂未获取到配送轨迹，请稍后重试");
        }
        List<Map<String, String>> nodes = new ArrayList<>();
        for (TrackPoint p : points) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("time", p.getTime());
            m.put("desc", p.getDesc());
            nodes.add(m);
        }
        if (isOrderBiz(d.getBizType())) {
            persistOrderTrack(d.getBizNo(), deliveryNo, d.getProviderName(), toLogisticsStatus(d.getStatus()), nodes);
        }
        List<LogisticsTrack> tracks = LogisticsTimelineBuilder.build(nodes, d.getProviderName(),
                toLogisticsStatus(d.getStatus())).getTracks();
        return LocalDeliveryVO.of(d, tracks);
    }

    // ===== 查询 =====

    /** 我的配送单分页。 */
    public IPage<LocalDelivery> myList(Long userId, int page, int size) {
        return deliveryMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<LocalDelivery>()
                .eq(LocalDelivery::getUserId, userId)
                .orderByDesc(LocalDelivery::getCreatedAt));
    }

    /** 配送单详情（本人）。 */
    public LocalDeliveryVO detail(Long userId, String deliveryNo) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (userId != null && !userId.equals(d.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "无权查看该配送单");
        }
        return LocalDeliveryVO.of(d, readOrderTracks(d.getBizNo(), d.getProviderName()));
    }

    /** 后台列表（REQ-08 机构范围由调用方收敛，orgId 为空即超管视角）。 */
    public IPage<LocalDelivery> adminList(int page, int size, String status, String bizNo, String city) {
        LambdaQueryWrapper<LocalDelivery> q = new LambdaQueryWrapper<LocalDelivery>()
                .eq(status != null && !status.isBlank(), LocalDelivery::getStatus, status)
                .eq(bizNo != null && !bizNo.isBlank(), LocalDelivery::getBizNo, bizNo)
                .eq(city != null && !city.isBlank(), LocalDelivery::getCity, city)
                .orderByDesc(LocalDelivery::getCreatedAt);
        return deliveryMapper.selectPage(new Page<>(page, size), q);
    }

    /** 后台详情。 */
    public LocalDeliveryVO adminDetail(String deliveryNo) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        return LocalDeliveryVO.of(d, readOrderTracks(d.getBizNo(), d.getProviderName()));
    }

    // ===== 内部工具 =====

    private LocalDelivery advance(String deliveryNo, String to, String operator) {
        LocalDelivery d = mustGetByNo(deliveryNo);
        if (!LocalDeliveryStateMachine.canTransition(d.getStatus(), to)) {
            throw new BizException(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED);
        }
        LocalDelivery upd = new LocalDelivery();
        upd.setId(d.getId());
        upd.setStatus(to);
        deliveryMapper.updateById(upd);
        metrics.increment("local.delivery." + to.toLowerCase());
        return d;
    }

    private LocalDelivery mustGetByNo(String deliveryNo) {
        LocalDelivery d = deliveryMapper.selectOne(new LambdaQueryWrapper<LocalDelivery>()
                .eq(LocalDelivery::getDeliveryNo, deliveryNo).last("LIMIT 1"));
        if (d == null) {
            throw new BizException(Code.LOCAL_DELIVERY_NOT_FOUND);
        }
        return d;
    }

    private static boolean isOrderBiz(String bizType) {
        return "ORDER".equalsIgnoreCase(bizType);
    }

    private static String toLogisticsStatus(String status) {
        if (status == null) {
            return "transport";
        }
        return switch (status) {
            case "SIGNED" -> "signed";
            case "FAILED", "CANCELED" -> "exception";
            default -> "transport";
        };
    }

    /**
     * 写入/更新既有 {@code t_logistics}（复用 D5 物流表，不新建轨迹表）。
     * nodes 为空表示仅初始化一条「已创建」节点。
     */
    private void persistOrderTrack(String bizNo, String deliveryNo, String company, String status) {
        persistOrderTrack(bizNo, deliveryNo, company, status, List.of());
    }

    private void persistOrderTrack(String bizNo, String deliveryNo, String company, String status,
                                   List<Map<String, String>> nodes) {
        List<Map<String, String>> detail = nodes;
        if (detail == null || detail.isEmpty()) {
            detail = List.of(node(java.time.LocalDateTime.now(),
                    "【" + deliveryNo + "】同城配送已创建（" + company + "）"));
        }
        Logistics l = logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>()
                .eq(Logistics::getOrderNo, bizNo).last("LIMIT 1"));
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            json = "[]";
        }
        if (l == null) {
            l = new Logistics();
            l.setOrderNo(bizNo);
            l.setLogisticsNo(deliveryNo);
            l.setCompany(company);
            l.setStatus(toLogisticsStatus(status));
            l.setDetailJson(json);
            logisticsMapper.insert(l);
        } else {
            l.setLogisticsNo(deliveryNo);
            l.setCompany(company);
            l.setStatus(toLogisticsStatus(status));
            l.setDetailJson(json);
            logisticsMapper.updateById(l);
        }
    }

    private List<LogisticsTrack> readOrderTracks(String bizNo, String company) {
        Logistics l = logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>()
                .eq(Logistics::getOrderNo, bizNo).last("LIMIT 1"));
        if (l == null || l.getDetailJson() == null || l.getDetailJson().isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> nodes = objectMapper.readValue(l.getDetailJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return LogisticsTimelineBuilder.build(nodes, l.getCompany() == null ? company : l.getCompany(),
                    l.getStatus()).getTracks();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, String> node(java.time.LocalDateTime t, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", t.format(com.idlefish.trade.common.util.DateTimeUtil.FMT));
        m.put("desc", desc);
        return m;
    }

    private void notifyBestEffort(Long userId, NotificationType type, String bizId, String title, String content) {
        try {
            notificationService.notify(userId, type, bizId, title, content);
        } catch (Exception ignored) {
        }
    }
}
