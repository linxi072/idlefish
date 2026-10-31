package com.idlefish.trade.pricing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.pricing.PriceEstimator;
import com.idlefish.trade.pricing.entity.PriceEstimateAudit;
import com.idlefish.trade.pricing.entity.PriceEstimateConfig;
import com.idlefish.trade.pricing.entity.PriceSample;
import com.idlefish.trade.pricing.mapper.PriceEstimateAuditMapper;
import com.idlefish.trade.pricing.mapper.PriceEstimateConfigMapper;
import com.idlefish.trade.pricing.mapper.PriceSampleMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 估价数据访问（ITER-F03）：配置 / 样本 / 审计 / 商品的只读与写回，由 {@link PricingService} 构造器内持有。
 * 纯函数映射与计算下沉至 {@link PriceEstimator} 与 {@link PricingViews}；本类只承载 Mapper 交互，
 * 不持有业务编排（写后留痕、指标上报、告警等副作用留在服务层）。
 */
class PricingStore {

    private final PriceSampleMapper sampleMapper;
    private final PriceEstimateConfigMapper configMapper;
    private final PriceEstimateAuditMapper auditMapper;
    private final ItemMapper itemMapper;

    PricingStore(PriceSampleMapper sampleMapper,
                 PriceEstimateConfigMapper configMapper,
                 PriceEstimateAuditMapper auditMapper,
                 ItemMapper itemMapper) {
        this.sampleMapper = sampleMapper;
        this.configMapper = configMapper;
        this.auditMapper = auditMapper;
        this.itemMapper = itemMapper;
    }

    // ===================== 读 =====================

    Item selectItemById(Long itemId) {
        return itemId == null ? null : itemMapper.selectById(itemId);
    }

    void insertSample(PriceSample s) {
        sampleMapper.insert(s);
    }

    PriceEstimateConfig config(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return configMapper.selectOne(new LambdaQueryWrapper<PriceEstimateConfig>()
                .eq(PriceEstimateConfig::getCategoryId, categoryId));
    }

    /** 品类成交中位数（任意成色，作为「全新」参考基线）；无样本返回 null。 */
    Long categoryMedian(Long categoryId) {
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

    /** 品类基线（全新参考）：配置有效且基准价 > 0 取配置基准价，否则取品类中位数。 */
    Long rawBaseline(Long categoryId, PriceEstimateConfig cfg) {
        if (cfg != null && PricingService.STATUS_ENABLED.equals(cfg.getStatus())
                && cfg.getBasePriceFen() != null && cfg.getBasePriceFen() > 0) {
            return cfg.getBasePriceFen();
        }
        return categoryMedian(categoryId);
    }

    /** 同成色成交价列表（去 null/非正）。 */
    List<Long> sameConditionSamples(Long categoryId, int level) {
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

    /** REQ-05 校准全量：样本与配置覆盖到的全部品类 ID（并集）。 */
    Set<Long> allCategoryIds() {
        Set<Long> ids = new HashSet<>();
        sampleMapper.selectList(new LambdaQueryWrapper<PriceSample>()
                        .select(PriceSample::getCategoryId))
                .forEach(s -> { if (s.getCategoryId() != null) ids.add(s.getCategoryId()); });
        configMapper.selectList(new LambdaQueryWrapper<PriceEstimateConfig>()
                        .select(PriceEstimateConfig::getCategoryId))
                .forEach(c -> { if (c.getCategoryId() != null) ids.add(c.getCategoryId()); });
        return ids;
    }

    /** 异常低价送审列表（风控关注，REQ-10）。 */
    List<PriceEstimateAudit> abnormalList() {
        return auditMapper.selectList(new LambdaQueryWrapper<PriceEstimateAudit>()
                .eq(PriceEstimateAudit::getBizType, PricingService.BIZ_ABNORMAL_LOW)
                .orderByDesc(PriceEstimateAudit::getCreatedAt));
    }

    /** 估价操作审计日志（REQ-08 留痕查询）。 */
    List<PriceEstimateAudit> auditLog() {
        return auditMapper.selectList(new LambdaQueryWrapper<PriceEstimateAudit>()
                .orderByDesc(PriceEstimateAudit::getCreatedAt));
    }

    // ===================== 写 =====================

    void writeAudit(Long categoryId, String bizType, Long bizId, String beforeJson,
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

    /**
     * 保存/更新品类估价配置（基准价/系数/状态），返回持久化后的最新配置（供调用方留痕）。
     * 无既有配置则新增，否则按非空字段增量更新。
     */
    PriceEstimateConfig saveConfigUpsert(Long categoryId, Long basePriceFen, BigDecimal multiplier,
                                         String status, Long operatorId) {
        PriceEstimateConfig cfg = config(categoryId);
        if (cfg == null) {
            cfg = new PriceEstimateConfig();
            cfg.setCategoryId(categoryId);
            cfg.setBasePriceFen(basePriceFen);
            cfg.setMultiplier(multiplier == null ? BigDecimal.ONE : multiplier);
            cfg.setStatus(status == null || status.isBlank() ? PricingService.STATUS_ENABLED : status);
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
        return config(categoryId);
    }

    /**
     * 运营系数覆盖（REQ-08）：在既有配置上调整全局系数，返回最新配置。
     * 配置不存在抛 {@link Code#PRICING_CONFIG_NOT_FOUND}（调用方通常已前置校验，此处为防御性兜底）。
     */
    PriceEstimateConfig overrideConfig(Long categoryId, BigDecimal multiplier, Long operatorId) {
        PriceEstimateConfig cfg = config(categoryId);
        if (cfg == null) {
            throw new BizException(Code.PRICING_CONFIG_NOT_FOUND, "估价配置不存在，请先配置基准价");
        }
        PriceEstimateConfig upd = new PriceEstimateConfig();
        upd.setId(cfg.getId());
        upd.setMultiplier(multiplier);
        upd.setOperatorId(operatorId);
        configMapper.updateById(upd);
        return config(categoryId);
    }

    /** 校准（REQ-05）：基于样本中位数重算基准价，返回最新配置。 */
    PriceEstimateConfig recalcUpsert(Long categoryId, Long median, Long operatorId) {
        PriceEstimateConfig cfg = config(categoryId);
        if (cfg == null) {
            cfg = new PriceEstimateConfig();
            cfg.setCategoryId(categoryId);
            cfg.setBasePriceFen(median);
            cfg.setMultiplier(BigDecimal.ONE);
            cfg.setStatus(PricingService.STATUS_ENABLED);
            cfg.setOperatorId(operatorId);
            configMapper.insert(cfg);
        } else {
            PriceEstimateConfig upd = new PriceEstimateConfig();
            upd.setId(cfg.getId());
            upd.setBasePriceFen(median);
            upd.setOperatorId(operatorId);
            configMapper.updateById(upd);
        }
        return config(categoryId);
    }
}
