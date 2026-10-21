package com.idlefish.trade.pricing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.pricing.PriceEstimator;
import com.idlefish.trade.pricing.dto.PriceConfigRequest;
import com.idlefish.trade.pricing.dto.PriceOverrideRequest;
import com.idlefish.trade.pricing.entity.PriceEstimateAudit;
import com.idlefish.trade.pricing.entity.PriceEstimateConfig;
import com.idlefish.trade.pricing.entity.PriceSample;
import com.idlefish.trade.pricing.mapper.PriceEstimateAuditMapper;
import com.idlefish.trade.pricing.mapper.PriceEstimateConfigMapper;
import com.idlefish.trade.pricing.mapper.PriceSampleMapper;
import com.idlefish.trade.pricing.vo.PriceDeviationVO;
import com.idlefish.trade.pricing.vo.PriceEstimateVO;
import com.idlefish.trade.publish.ReferencePriceProvider;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 估价服务（ITER-F03）：数据驱动估价引擎，并实现 {@link ReferencePriceProvider} 作为 U01 规则兜底数据源。
 *
 * <p>能力（对应 PRD REQ）：
 * <ul>
 *   <li>REQ-02 估价区间 + 样本数；REQ-03 样本不足规则兜底并标注；</li>
 *   <li>REQ-05 成交价回流样本（recordDeal，best-effort 不阻塞支付主流程）；</li>
 *   <li>REQ-08 运营系数覆盖 + 留痕（config / override）；</li>
 *   <li>REQ-09 偏离判定（deviation，提示不阻断）；</li>
 *   <li>REQ-10 异常低价标记送审（evaluateListing，持久化 + 告警，不阻断发布）；</li>
 *   <li>REQ-06 无数据 hasData=false，由前端隐藏入口，不阻塞。</li>
 * </ul>
 *
 * <p>金额恒「分」整型（REQ-01）；纯计算下沉到 {@link PriceEstimator}（REQ-07）。
 */
@Service
public class PricingService implements ReferencePriceProvider {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String BIZ_CONFIG_OVERRIDE = "CONFIG_OVERRIDE";
    public static final String BIZ_ABNORMAL_LOW = "ABNORMAL_LOW";
    public static final String BIZ_RECALC = "RECALC";

    private final PriceSampleMapper sampleMapper;
    private final PriceEstimateConfigMapper configMapper;
    private final PriceEstimateAuditMapper auditMapper;
    private final ItemMapper itemMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final ObjectMapper objectMapper;
    private final IdlefishProperties idlefishProperties;

    public PricingService(PriceSampleMapper sampleMapper,
                          PriceEstimateConfigMapper configMapper,
                          PriceEstimateAuditMapper auditMapper,
                          ItemMapper itemMapper,
                          NotificationService notificationService,
                          MetricsRegistry metrics,
                          ObjectMapper objectMapper,
                          IdlefishProperties idlefishProperties) {
        this.sampleMapper = sampleMapper;
        this.configMapper = configMapper;
        this.auditMapper = auditMapper;
        this.itemMapper = itemMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.idlefishProperties = idlefishProperties;
    }

    // ===================== U01 规则兜底数据源（ReferencePriceProvider） =====================

    /**
     * 品类基准价（分，全新参考）：优先运营配置基准价，否则取品类成交中位数。
     * 应用运营系数覆盖后返回；无数据返回 empty（U01 据此降级，REQ-05）。
     */
    @Override
    public Optional<Long> basePriceFen(Long categoryId, Integer conditionLevel) {
        if (categoryId == null) {
            return Optional.empty();
        }
        PriceEstimateConfig cfg = config(categoryId);
        Long raw;
        if (cfg != null && STATUS_ENABLED.equals(cfg.getStatus())
                && cfg.getBasePriceFen() != null && cfg.getBasePriceFen() > 0) {
            raw = cfg.getBasePriceFen();
        } else {
            raw = categoryMedian(categoryId);
        }
        if (raw == null || raw <= 0) {
            return Optional.empty();
        }
        if (cfg != null && cfg.getMultiplier() != null) {
            raw = Math.round(raw * cfg.getMultiplier().doubleValue());
        }
        return Optional.of(raw);
    }

    // ===================== 估价主入口 =====================

    /**
     * REQ-02 估价：同成色样本分位优先；不足则规则兜底；再无则 hasData=false（REQ-06 降级）。
     */
    public PriceEstimateVO estimate(Long categoryId, Integer conditionLevel) {
        PriceEstimateVO vo = new PriceEstimateVO();
        int level = conditionLevel == null ? 3 : conditionLevel;

        List<Long> samples = sameConditionSamples(categoryId, level);
        long[] range = PriceEstimator.sampleRange(samples);
        if (range != null) {
            vo.setHasData(true);
            vo.setSource(PriceEstimator.SOURCE_SAMPLE);
            vo.setMinFen(range[0]);
            vo.setMedianFen(range[1]);
            vo.setMaxFen(range[2]);
            vo.setSampleCount(samples.size());
            vo.setNote("基于同类同成色历史成交分位估价（参考值，定价权归卖家）");
            metrics.increment("pricing.estimate.sample");
            return vo;
        }

        // REQ-03 样本不足 → 规则兜底（基准价 × 系数 × 成色系数 ±10%）
        PriceEstimateConfig cfg = config(categoryId);
        Long base = rawBaseline(categoryId, cfg);
        if (base != null && base > 0) {
            double mult = (cfg != null && cfg.getMultiplier() != null)
                    ? cfg.getMultiplier().doubleValue() : 1.0;
            long[] rr = PriceEstimator.ruleFallback(Math.round(base * mult), level);
            if (rr != null) {
                vo.setHasData(true);
                vo.setSource(PriceEstimator.SOURCE_RULE);
                vo.setMinFen(rr[0]);
                vo.setMedianFen(rr[1]);
                vo.setMaxFen(rr[2]);
                vo.setSampleCount(samples.size());
                vo.setNote("参考样本不足，采用规则兜底（基准价×运营系数，仅供参考）");
                metrics.increment("pricing.estimate.rule");
                return vo;
            }
        }

        vo.setHasData(false);
        vo.setNote("暂无足够成交样本与基准价，估价暂不可用（不阻塞发布/议价）");
        metrics.increment("pricing.estimate.nodata");
        return vo;
    }

    /**
     * REQ-09 偏离判定：给定报价，返回相对建议区间的偏离类型（提示不阻断）。
     */
    public PriceDeviationVO deviation(Long categoryId, Integer conditionLevel, long price) {
        PriceDeviationVO vo = new PriceDeviationVO();
        PriceEstimateVO e = estimate(categoryId, conditionLevel);
        vo.setHasData(e.isHasData());
        vo.setMinFen(e.getMinFen());
        vo.setMedianFen(e.getMedianFen());
        vo.setMaxFen(e.getMaxFen());
        vo.setDeviation(e.isHasData()
                ? PriceEstimator.checkDeviation(price, e.getMinFen(), e.getMaxFen())
                : PriceEstimator.DEVIATION_NONE);
        return vo;
    }

    // ===================== REQ-05 成交价回流 =====================

    /**
     * REQ-05 成交价回流样本：支付成功（onOrderPaid）best-effort 调用，不影响支付主流程。
     */
    public void recordDeal(Order order, PayOrder po) {
        if (order == null || po == null || po.getAmount() == null) {
            return;
        }
        Long itemId = order.getItemId();
        if (itemId == null) {
            return;
        }
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            return;
        }
        PriceSample s = new PriceSample();
        s.setItemId(itemId);
        s.setCategoryId(item.getCategoryId());
        s.setConditionLevel(item.getConditionLevel());
        s.setDealPriceFen(po.getAmount());
        s.setDealAt(LocalDateTime.now());
        s.setSource("deal");
        sampleMapper.insert(s);
        metrics.increment("pricing.sample.recorded");
    }

    // ===================== REQ-10 异常低价送审 =====================

    /**
     * REQ-10 异常低价标记送审：发布（best-effort）时若挂牌价低于估价下限 50%，
     * 持久化送审记录 + 告警运营，但不阻断发布（合规红线：不得直接下架）。
     */
    public void evaluateListing(Item item) {
        if (item == null || item.getPrice() == null || item.getPrice() <= 0
                || item.getCategoryId() == null) {
            return;
        }
        PriceEstimateVO vo = estimate(item.getCategoryId(), item.getConditionLevel());
        if (!vo.isHasData()) {
            return;
        }
        if (PriceEstimator.isAbnormalLow(item.getPrice(), vo.getMinFen())) {
            try {
                PriceEstimateAudit a = new PriceEstimateAudit();
                a.setCategoryId(item.getCategoryId());
                a.setBizType(BIZ_ABNORMAL_LOW);
                a.setBizId(item.getId());
                a.setBeforeJson(json(Map.of(
                        "listedPriceFen", item.getPrice(),
                        "minFen", vo.getMinFen(),
                        "maxFen", vo.getMaxFen())));
                a.setReason("异常低价：挂牌价低于估价区间下限 50%，转人工送审（不阻断发布）");
                a.setOperatorId(0L);
                auditMapper.insert(a);
                notifySafely(abnormalAlertAdmin(), NotificationType.SYSTEM_ALERT,
                        String.valueOf(item.getId()), "异常低价待审",
                        "商品 " + item.getId() + " 挂牌价 " + item.getPrice()
                                + " 分，低于估价下限，请人工送审");
                metrics.increment("pricing.abnormal_low");
            } catch (Exception ignored) {
                // 送审标记失败不影响发布主流程
            }
        }
    }

    // ===================== REQ-08 运营配置与留痕 =====================

    /** 读取品类估价配置（无则 null）。 */
    public PriceEstimateConfig getConfig(Long categoryId) {
        return config(categoryId);
    }

    /** REQ-08 保存/更新品类估价配置（基准价/系数/状态），并留痕。 */
    public void saveConfig(Long categoryId, Long basePriceFen, BigDecimal multiplier,
                           String status, Long operatorId) {
        if (categoryId == null) {
            throw new BizException(Code.PARAM_MISSING, "类目 ID 必填");
        }
        PriceEstimateConfig cfg = config(categoryId);
        String before = cfg == null ? null : json(cfg);
        if (cfg == null) {
            cfg = new PriceEstimateConfig();
            cfg.setCategoryId(categoryId);
            cfg.setBasePriceFen(basePriceFen);
            cfg.setMultiplier(multiplier == null ? BigDecimal.ONE : multiplier);
            cfg.setStatus(status == null || status.isBlank() ? STATUS_ENABLED : status);
            cfg.setOperatorId(operatorId);
            configMapper.insert(cfg);
        } else {
            PriceEstimateConfig upd = new PriceEstimateConfig();
            upd.setId(cfg.getId());
            if (basePriceFen != null) {
                upd.setBasePriceFen(basePriceFen);
            }
            if (multiplier != null) {
                upd.setMultiplier(multiplier);
            }
            if (status != null && !status.isBlank()) {
                upd.setStatus(status);
            }
            upd.setOperatorId(operatorId);
            configMapper.updateById(upd);
        }
        PriceEstimateConfig after = config(categoryId);
        writeAudit(categoryId, BIZ_CONFIG_OVERRIDE, after.getId(), before, json(after),
                operatorId, "运营配置基准价/系数/状态");
        metrics.increment("pricing.config.saved");
    }

    /** REQ-08 运营系数覆盖：在既有配置上调整全局系数并留痕（REQ-08）。 */
    public void override(Long categoryId, BigDecimal multiplier, String reason, Long operatorId) {
        if (categoryId == null) {
            throw new BizException(Code.PARAM_MISSING, "类目 ID 必填");
        }
        if (multiplier == null || multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(Code.PARAM_INVALID, "系数须为正数");
        }
        PriceEstimateConfig cfg = config(categoryId);
        if (cfg == null) {
            throw new BizException(Code.PRICING_CONFIG_NOT_FOUND, "估价配置不存在，请先配置基准价");
        }
        String before = json(cfg);
        PriceEstimateConfig upd = new PriceEstimateConfig();
        upd.setId(cfg.getId());
        upd.setMultiplier(multiplier);
        upd.setOperatorId(operatorId);
        configMapper.updateById(upd);
        PriceEstimateConfig after = config(categoryId);
        writeAudit(categoryId, BIZ_CONFIG_OVERRIDE, after.getId(), before, json(after),
                operatorId, "运营系数覆盖" + (reason == null ? "" : "：" + reason));
        metrics.increment("pricing.config.override");
    }

    /** REQ-05 校准：基于样本重算品类基准价（品类级中位数）。 */
    public void recalibrate(Long categoryId) {
        if (categoryId != null) {
            recalcOne(categoryId);
            return;
        }
        Set<Long> ids = new HashSet<>();
        sampleMapper.selectList(new LambdaQueryWrapper<PriceSample>()
                        .select(PriceSample::getCategoryId))
                .forEach(s -> { if (s.getCategoryId() != null) ids.add(s.getCategoryId()); });
        configMapper.selectList(new LambdaQueryWrapper<PriceEstimateConfig>()
                        .select(PriceEstimateConfig::getCategoryId))
                .forEach(c -> { if (c.getCategoryId() != null) ids.add(c.getCategoryId()); });
        ids.forEach(this::recalcOne);
    }

    /** 异常低价送审列表（风控关注，REQ-10）。 */
    public List<PriceEstimateAudit> abnormalList() {
        return auditMapper.selectList(new LambdaQueryWrapper<PriceEstimateAudit>()
                .eq(PriceEstimateAudit::getBizType, BIZ_ABNORMAL_LOW)
                .orderByDesc(PriceEstimateAudit::getCreatedAt));
    }

    /** 估价操作审计日志（REQ-08 留痕查询）。 */
    public List<PriceEstimateAudit> auditLog() {
        return auditMapper.selectList(new LambdaQueryWrapper<PriceEstimateAudit>()
                .orderByDesc(PriceEstimateAudit::getCreatedAt));
    }

    // ===================== 内部工具 =====================

    private void recalcOne(Long categoryId) {
        Long median = categoryMedian(categoryId);
        PriceEstimateConfig cfg = config(categoryId);
        if (cfg == null) {
            cfg = new PriceEstimateConfig();
            cfg.setCategoryId(categoryId);
            cfg.setBasePriceFen(median);
            cfg.setMultiplier(BigDecimal.ONE);
            cfg.setStatus(STATUS_ENABLED);
            cfg.setOperatorId(0L);
            configMapper.insert(cfg);
        } else {
            PriceEstimateConfig upd = new PriceEstimateConfig();
            upd.setId(cfg.getId());
            upd.setBasePriceFen(median);
            upd.setOperatorId(0L);
            configMapper.updateById(upd);
        }
        PriceEstimateConfig after = config(categoryId);
        writeAudit(categoryId, BIZ_RECALC, after.getId(), null,
                "{\"basePriceFen\":" + median + "}", 0L, "自动重算基准价（品类成交中位数）");
        metrics.increment("pricing.recalibrate");
    }

    private PriceEstimateConfig config(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return configMapper.selectOne(new LambdaQueryWrapper<PriceEstimateConfig>()
                .eq(PriceEstimateConfig::getCategoryId, categoryId));
    }

    private Long rawBaseline(Long categoryId, PriceEstimateConfig cfg) {
        if (cfg != null && STATUS_ENABLED.equals(cfg.getStatus())
                && cfg.getBasePriceFen() != null && cfg.getBasePriceFen() > 0) {
            return cfg.getBasePriceFen();
        }
        return categoryMedian(categoryId);
    }

    /** 品类成交中位数（任意成色，作为「全新」参考基线）；无样本返回 null。 */
    private Long categoryMedian(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        List<PriceSample> list = sampleMapper.selectList(new LambdaQueryWrapper<PriceSample>()
                .eq(PriceSample::getCategoryId, categoryId)
                .select(PriceSample::getDealPriceFen));
        if (list.isEmpty()) {
            return null;
        }
        List<Long> prices = new ArrayList<>();
        for (PriceSample s : list) {
            if (s.getDealPriceFen() != null && s.getDealPriceFen() > 0) {
                prices.add(s.getDealPriceFen());
            }
        }
        if (prices.isEmpty()) {
            return null;
        }
        List<Long> cleaned = PriceEstimator.cleanSamples(prices);
        if (cleaned.isEmpty()) {
            return null;
        }
        return PriceEstimator.percentile(cleaned, 0.5);
    }

    /** 同成色成交价列表（去 null/非正）。 */
    private List<Long> sameConditionSamples(Long categoryId, int level) {
        List<Long> out = new ArrayList<>();
        if (categoryId == null) {
            return out;
        }
        List<PriceSample> list = sampleMapper.selectList(new LambdaQueryWrapper<PriceSample>()
                .eq(PriceSample::getCategoryId, categoryId)
                .eq(PriceSample::getConditionLevel, level)
                .select(PriceSample::getDealPriceFen));
        for (PriceSample s : list) {
            if (s.getDealPriceFen() != null && s.getDealPriceFen() > 0) {
                out.add(s.getDealPriceFen());
            }
        }
        return out;
    }

    private void writeAudit(Long categoryId, String bizType, Long bizId, String beforeJson,
                            String afterJson, Long operatorId, String reason) {
        PriceEstimateAudit a = new PriceEstimateAudit();
        a.setCategoryId(categoryId);
        a.setBizType(bizType);
        a.setBizId(bizId);
        a.setBeforeJson(beforeJson);
        a.setAfterJson(afterJson);
        a.setOperatorId(operatorId);
        a.setReason(reason);
        auditMapper.insert(a);
    }

    private Long abnormalAlertAdmin() {
        if (idlefishProperties != null && idlefishProperties.getPricing() != null
                && idlefishProperties.getPricing().getAlertAdminUserId() != null) {
            return idlefishProperties.getPricing().getAlertAdminUserId();
        }
        if (idlefishProperties != null && idlefishProperties.getNotify() != null
                && idlefishProperties.getNotify().getAlertAdminUserId() != null) {
            return idlefishProperties.getNotify().getAlertAdminUserId();
        }
        return 1L;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void notifySafely(Long userId, NotificationType type, String bizId, String title, String content) {
        try {
            notificationService.notify(userId, type, bizId, title, content);
        } catch (Exception ignored) {
        }
    }
}
