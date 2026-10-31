package com.idlefish.trade.pricing.service;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * 数据访问下沉 {@link PricingStore}，纯函数映射下沉 {@link PricingViews}；本类只做编排与副作用（指标/告警/留痕）。
 */
@Service
public class PricingService implements ReferencePriceProvider {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String BIZ_CONFIG_OVERRIDE = "CONFIG_OVERRIDE";
    public static final String BIZ_ABNORMAL_LOW = "ABNORMAL_LOW";
    public static final String BIZ_RECALC = "RECALC";

    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final ObjectMapper objectMapper;
    private final IdlefishProperties idlefishProperties;
    private final PricingStore store;

    public PricingService(PriceSampleMapper sampleMapper,
                          PriceEstimateConfigMapper configMapper,
                          PriceEstimateAuditMapper auditMapper,
                          ItemMapper itemMapper,
                          NotificationService notificationService,
                          MetricsRegistry metrics,
                          ObjectMapper objectMapper,
                          IdlefishProperties idlefishProperties) {
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.idlefishProperties = idlefishProperties;
        this.store = new PricingStore(sampleMapper, configMapper, auditMapper, itemMapper);
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
        PriceEstimateConfig cfg = store.config(categoryId);
        Long raw;
        if (cfg != null && STATUS_ENABLED.equals(cfg.getStatus())
                && cfg.getBasePriceFen() != null && cfg.getBasePriceFen() > 0) {
            raw = cfg.getBasePriceFen();
        } else {
            raw = store.categoryMedian(categoryId);
        }
        if (raw == null || raw <= 0) {
            return Optional.empty();
        }
        if (cfg != null && cfg.getMultiplier() != null) {
            raw = PricingViews.applyMultiplier(raw, cfg.getMultiplier());
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

        List<Long> samples = store.sameConditionSamples(categoryId, level);
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
        PriceEstimateConfig cfg = store.config(categoryId);
        Long base = store.rawBaseline(categoryId, cfg);
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
        Item item = store.selectItemById(itemId);
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
        store.insertSample(s);
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
                a.setBeforeJson(PricingViews.safeJson(objectMapper, Map.of(
                        "listedPriceFen", item.getPrice(),
                        "minFen", vo.getMinFen(),
                        "maxFen", vo.getMaxFen())));
                a.setReason("异常低价：挂牌价低于估价区间下限 50%，转人工送审（不阻断发布）");
                a.setOperatorId(0L);
                store.writeAudit(item.getCategoryId(), BIZ_ABNORMAL_LOW, item.getId(),
                        a.getBeforeJson(), null, 0L, a.getReason());
                notifySafely(PricingViews.resolveAlertAdmin(idlefishProperties), NotificationType.SYSTEM_ALERT,
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
        return store.config(categoryId);
    }

    /** REQ-08 保存/更新品类估价配置（基准价/系数/状态），并留痕。 */
    public void saveConfig(Long categoryId, Long basePriceFen, BigDecimal multiplier,
                           String status, Long operatorId) {
        if (categoryId == null) {
            throw new BizException(Code.PARAM_MISSING, "类目 ID 必填");
        }
        PriceEstimateConfig cfg = store.config(categoryId);
        String before = cfg == null ? null : PricingViews.safeJson(objectMapper, cfg);
        PriceEstimateConfig after = store.saveConfigUpsert(categoryId, basePriceFen, multiplier, status, operatorId);
        store.writeAudit(categoryId, BIZ_CONFIG_OVERRIDE, after.getId(), before,
                PricingViews.safeJson(objectMapper, after), operatorId, "运营配置基准价/系数/状态");
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
        PriceEstimateConfig cfg = store.config(categoryId);
        if (cfg == null) {
            throw new BizException(Code.PRICING_CONFIG_NOT_FOUND, "估价配置不存在，请先配置基准价");
        }
        String before = PricingViews.safeJson(objectMapper, cfg);
        PriceEstimateConfig after = store.overrideConfig(categoryId, multiplier, operatorId);
        store.writeAudit(categoryId, BIZ_CONFIG_OVERRIDE, after.getId(), before,
                PricingViews.safeJson(objectMapper, after), operatorId,
                "运营系数覆盖" + (reason == null ? "" : "：" + reason));
        metrics.increment("pricing.config.override");
    }

    /** REQ-05 校准：基于样本重算品类基准价（品类级中位数）。 */
    public void recalibrate(Long categoryId) {
        if (categoryId != null) {
            recalcOne(categoryId);
            return;
        }
        store.allCategoryIds().forEach(this::recalcOne);
    }

    /** 异常低价送审列表（风控关注，REQ-10）。 */
    public List<PriceEstimateAudit> abnormalList() {
        return store.abnormalList();
    }

    /** 估价操作审计日志（REQ-08 留痕查询）。 */
    public List<PriceEstimateAudit> auditLog() {
        return store.auditLog();
    }

    // ===================== 内部工具 =====================

    private void recalcOne(Long categoryId) {
        Long median = store.categoryMedian(categoryId);
        PriceEstimateConfig after = store.recalcUpsert(categoryId, median, 0L);
        store.writeAudit(categoryId, BIZ_RECALC, after.getId(), null,
                "{\"basePriceFen\":" + median + "}", 0L, "自动重算基准价（品类成交中位数）");
        metrics.increment("pricing.recalibrate");
    }

    private void notifySafely(Long userId, NotificationType type, String bizId, String title, String content) {
        try {
            notificationService.notify(userId, type, bizId, title, content);
        } catch (Exception ignored) {
        }
    }
}
