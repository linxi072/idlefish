package com.idlefish.trade.publish.service;

import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.AttrTemplate;
import com.idlefish.trade.item.service.AttrTemplateService;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.publish.AiAssistGateway;
import com.idlefish.trade.publish.PublishAssistCalculator;
import com.idlefish.trade.publish.ReferencePriceProvider;
import com.idlefish.trade.publish.dto.DraftRequest;
import com.idlefish.trade.publish.dto.RecognizeRequest;
import com.idlefish.trade.publish.vo.DraftSuggestion;
import com.idlefish.trade.publish.vo.PriceSuggestion;
import com.idlefish.trade.publish.vo.RecognizeSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 小程序发布流程智能化服务（U01）：把「图片识别 / 估价 / 草稿生成」做成可降级的能力。
 *
 * <p>能力优先级（均不阻塞发布，REQ-05）：
 * <ol>
 *   <li>AI 结果（{@link AiAssistGateway}，依赖 ITER-F03）：识别类目 / 估价 / 草稿；</li>
 *   <li>规则兜底：无 AI 时，草稿用模板（始终可用），估价用 {@link ReferencePriceProvider} 基准价（接入历史成交后生效）；</li>
 *   <li>显式无数据：AI 与基准价均缺失时，返回 available/hasData=false，前端隐藏智能区块（REQ-05 降级）。</li>
 * </ol>
 *
 * <p>数据来源（真实、确定可用）：类目名称 {@link CategoryService#nameOf}、属性模板 {@link AttrTemplateService}（REQ-03）。
 */
@Service
public class PublishAssistService {

    private final CategoryService categoryService;
    private final AttrTemplateService attrTemplateService;
    private final MetricsRegistry metrics;
    private final Optional<AiAssistGateway> aiGateway;
    private final Optional<ReferencePriceProvider> refPrice;

    public PublishAssistService(CategoryService categoryService, AttrTemplateService attrTemplateService,
                               MetricsRegistry metrics, Optional<AiAssistGateway> aiGateway,
                               Optional<ReferencePriceProvider> refPrice) {
        this.categoryService = categoryService;
        this.attrTemplateService = attrTemplateService;
        this.metrics = metrics;
        this.aiGateway = aiGateway;
        this.refPrice = refPrice;
    }

    /**
     * REQ-01 图片识别建议类目：优先 AI；不可用时 available=false（REQ-05 降级，前端手工选类目）。
     */
    public RecognizeSuggestion recognize(RecognizeRequest req) {
        RecognizeSuggestion r = new RecognizeSuggestion();
        if (aiGateway.isPresent()) {
            Optional<AiAssistGateway.CategorySuggestion> s =
                    aiGateway.get().recognizeCategory(req.getImageUrl(), req.getImageMeta());
            if (s.isPresent()) {
                AiAssistGateway.CategorySuggestion c = s.get();
                r.setAvailable(true);
                r.setCategoryId(c.getCategoryId());
                r.setCategoryName(c.getCategoryName());
                r.setConfidence(c.getConfidence());
                metrics.increment("publish.assist.ai.used");
                return r;
            }
        }
        r.setAvailable(false);
        metrics.increment("publish.assist.ai.unavailable");
        return r;
    }

    /**
     * REQ-03 按类目加载属性模板（真实数据，确定可用）。类目不存在抛 CATEGORY_NOT_FOUND。
     */
    public List<AttrTemplate> attributes(Long categoryId) {
        categoryService.getById(categoryId); // 校验类目存在
        return attrTemplateService.listByCategory(categoryId);
    }

    /**
     * REQ-04 估价建议：优先 AI → 规则基准价兜底；均无则 hasData=false（REQ-05 降级）。
     */
    public PriceSuggestion price(Long categoryId, Integer conditionLevel) {
        int level = conditionLevel == null ? 3 : conditionLevel;
        PriceSuggestion p = new PriceSuggestion();
        if (aiGateway.isPresent()) {
            Optional<AiAssistGateway.PriceAiSuggestion> s = aiGateway.get().suggestPrice(categoryId, level);
            if (s.isPresent()) {
                p.setHasData(true);
                p.setSource("ai");
                p.setMinFen(s.get().getMinFen());
                p.setMaxFen(s.get().getMaxFen());
                metrics.increment("publish.assist.price.ai");
                return p;
            }
        }
        if (refPrice.isPresent()) {
            Optional<Long> base = refPrice.get().basePriceFen(categoryId, level);
            if (base.isPresent()) {
                long[] range = PublishAssistCalculator.suggestPriceRange(base.get(), level);
                if (range != null) {
                    p.setHasData(true);
                    p.setSource("rule");
                    p.setMinFen(range[0]);
                    p.setMaxFen(range[1]);
                    metrics.increment("publish.assist.price.rule");
                    return p;
                }
            }
        }
        p.setHasData(false);
        metrics.increment("publish.assist.price.nodata");
        return p;
    }

    /**
     * REQ-02 草稿建议：优先 AI；否则规则模板兜底（始终可用）。
     */
    public DraftSuggestion draft(DraftRequest req) {
        DraftSuggestion d = new DraftSuggestion();
        String catName = categoryService.nameOf(req.getCategoryId());
        int level = req.getConditionLevel() == null ? 3 : req.getConditionLevel();
        if (aiGateway.isPresent()) {
            Optional<AiAssistGateway.DraftAiSuggestion> s =
                    aiGateway.get().generateDraft(req.getCategoryId(), level, req.getTitle());
            if (s.isPresent()) {
                AiAssistGateway.DraftAiSuggestion a = s.get();
                d.setSource("ai");
                d.setAiAvailable(true);
                d.setTitle(a.getTitle());
                d.setDescription(a.getDescription());
                metrics.increment("publish.assist.draft.ai");
                return d;
            }
        }
        // REQ-02 规则兜底：模板生成，保证发布流程始终可走通
        d.setSource("rule");
        d.setAiAvailable(false);
        d.setTitle(PublishAssistCalculator.draftTitle(catName, level));
        d.setDescription(PublishAssistCalculator.draftDescription(catName, level, req.getTitle()));
        metrics.increment("publish.assist.draft.rule");
        return d;
    }
}
