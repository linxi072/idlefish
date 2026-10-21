package com.idlefish.trade.search.enhance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.enhance.dto.BucketResult;
import com.idlefish.trade.search.enhance.entity.AbExperiment;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.term.SearchTermFilter;
import com.idlefish.trade.search.term.service.SearchTermService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 搜索增强编排服务（ITER-F04 搜索推荐深化 / REQ-02/03/04/06/08/09）。
 * <p>
 * 单次增强检索的执行顺序：
 * ① 归一化 + 屏蔽词拦截（REQ-08，命中返回空、不报错、不记录）；
 * ② 关键词主召回（复用既有 {@link SearchService}）；
 * ③ 语义（同义词扩展）召回并融合去重（REQ-02/03，降级安全）；
 * ④ 实时兴趣/意图加权（REQ-01/09，来自 {@link UserRealtimeFeatureService}）与 AB 实验偏移（REQ-06）；
 * ⑤ 零结果兜底「猜你想找」（REQ-04，同义词扩展 + 热搜词召回）；
 * 全程任一外部依赖异常均降级为安全结果，绝不阻断主流程。
 */
@Service
public class SearchEnhanceService {

    private final SearchService searchService;
    private final SearchTermService termService;
    private final SemanticRecallPort semanticRecall;
    private final UserRealtimeFeatureService featureService;
    private final AbExperimentService abService;
    private final MetricsRegistry metrics;

    public SearchEnhanceService(SearchService searchService, SearchTermService termService,
                               SemanticRecallPort semanticRecall, UserRealtimeFeatureService featureService,
                               AbExperimentService abService, MetricsRegistry metrics) {
        this.searchService = searchService;
        this.termService = termService;
        this.semanticRecall = semanticRecall;
        this.featureService = featureService;
        this.abService = abService;
        this.metrics = metrics;
    }

    /**
     * 增强检索主入口。
     *
     * @param q      检索条件（关键词/类目/价格/城市/排序/分页）
     * @param userId 用户 ID（可空：空则不做实时加权与分桶）
     * @return 融合排序后的分页结果（零结果时自动兜底为「猜你想找」）
     */
    public IPage<ItemVO> enhancedSearch(ItemQueryDTO q, Long userId) {
        int page = Math.max(q.getPage() == null ? 1 : q.getPage(), 1);
        int size = Math.max(q.getSize() == null ? 20 : q.getSize(), 1);
        Page<ItemVO> empty = new Page<>(page, size);

        String keyword = SearchTermFilter.normalize(q.getKeyword());

        // REQ-08 屏蔽词：命中直接返回空（不报错、不记录）
        if (!keyword.isEmpty() && termService.isBlocked(keyword)) {
            metrics.increment("search.enhance.blocked");
            return empty;
        }

        // ① 关键词主召回（best-effort）
        ItemQueryDTO base = clone(q);
        base.setKeyword(keyword);
        IPage<ItemVO> primaryPage;
        try {
            primaryPage = searchService.search(base);
        } catch (RuntimeException ex) {
            primaryPage = new Page<>(page, size);
        }
        List<ItemVO> primary = (primaryPage == null || primaryPage.getRecords() == null)
                ? List.of() : primaryPage.getRecords();

        // ② 语义（同义词扩展）召回，降级安全
        List<ItemVO> semantic = List.of();
        if (!keyword.isEmpty()) {
            try {
                semantic = semanticRecall.recall(keyword, base);
            } catch (RuntimeException ex) {
                semantic = List.of();
            }
        }

        // ③ 实时兴趣/意图（REQ-01/09）
        Map<Long, Double> realtime = featureService.getRealtimeInterest(userId);
        try {
            featureService.recordBehavior(userId, "search", keyword); // 搜索意图反哺
        } catch (RuntimeException ignore) {
            // best-effort
        }

        // ④ AB 实验偏移（REQ-06）
        double recallBoost = 1.0;
        try {
            AbExperiment exp = abService.currentEffective();
            if (exp != null) {
                BucketResult bucket = abService.assignTo(exp, userId);
                if (bucket.effective() && "TREATMENT".equals(bucket.group())) {
                    Object boost = bucket.params().get("recallBoost");
                    if (boost instanceof Number n) {
                        recallBoost = n.doubleValue();
                    }
                }
            }
        } catch (RuntimeException ignore) {
            // best-effort：分桶异常不影响检索
        }

        // 融合排序（实时加权 + 热度 + 同城）；recallBoost 可能经 AB 分支重赋值，故用 final 副本供 lambda 捕获
        final double boost = recallBoost;
        ToDoubleFunction<ItemVO> scorer = it -> fusionScore(it, realtime, boost, q.getCity());
        List<ItemVO> fused = SearchFusion.fuseTwo(primary, semantic, scorer);

        // ⑤ 零结果兜底「猜你想找」（REQ-04）
        if (fused.isEmpty()) {
            fused = guessYouWant(keyword, size);
            metrics.increment("search.enhance.zerofallback");
        }

        // 分页截断
        int from = (page - 1) * size;
        int to = Math.min(from + size, fused.size());
        List<ItemVO> records = (from >= fused.size()) ? List.of() : fused.subList(from, to);
        Page<ItemVO> result = new Page<>(page, size, fused.size());
        result.setRecords(records);
        return result;
    }

    /**
     * 零结果兜底「猜你想找」（REQ-04）：基于关键词的同义词扩展 + 平台热搜词召回，合并去重。
     *
     * @param keyword 归一化关键词（空则纯热搜兜底）
     * @param limit   返回条数上限
     * @return 兜底商品列表（可能为空，但不得为 null）
     */
    public List<ItemVO> guessYouWant(String keyword, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }
        Set<String> words = new LinkedHashSet<>();
        if (keyword != null && !keyword.isBlank()) {
            try {
                words.addAll(semanticRecall.expansionKeywords(keyword));
            } catch (RuntimeException ignore) {
            }
        }
        try {
            words.addAll(termService.hotWords(limit));
        } catch (RuntimeException ignore) {
        }
        List<ItemVO> all = new ArrayList<>();
        for (String w : words) {
            try {
                ItemQueryDTO sq = new ItemQueryDTO();
                sq.setKeyword(w);
                sq.setPage(1);
                sq.setSize(limit);
                IPage<ItemVO> p = searchService.search(sq);
                if (p != null && p.getRecords() != null) {
                    all.addAll(p.getRecords());
                }
            } catch (RuntimeException ignore) {
                // 单路兜底召回失败不影响其他词
            }
        }
        return SearchFusion.topN(SearchFusion.dedupe(all), limit);
    }

    /** 行为上报（REQ-01）：供客户端回传 view_item/favorite/order_create/pay 即时更新实时兴趣。 */
    public void recordBehavior(Long userId, String event, String bizId) {
        featureService.recordBehavior(userId, event, bizId);
    }

    /** 融合打分：热度（×实验召回增益）×(1+实时兴趣) + 同城加成 + 实时兴趣直接加权。 */
    private double fusionScore(ItemVO it, Map<Long, Double> realtime, double recallBoost, String city) {
        double heat = (it.getViewCount() == null ? 0 : it.getViewCount())
                + (it.getFavCount() == null ? 0 : it.getFavCount()) * 5.0
                + (it.getLikeCount() == null ? 0 : it.getLikeCount()) * 3.0;
        heat *= recallBoost;
        double interest = (it.getCategoryId() != null && realtime != null)
                ? realtime.getOrDefault(it.getCategoryId(), 0.0) : 0.0;
        boolean sameCity = city != null && it.getCity() != null && it.getCity().equals(city.trim());
        return heat * (1.0 + interest) + (sameCity ? 1000.0 : 0.0) + interest * 10.0;
    }

    private ItemQueryDTO clone(ItemQueryDTO q) {
        ItemQueryDTO c = new ItemQueryDTO();
        c.setCategoryId(q.getCategoryId());
        c.setCity(q.getCity());
        c.setProvince(q.getProvince());
        c.setMinPrice(q.getMinPrice());
        c.setMaxPrice(q.getMaxPrice());
        c.setSort(q.getSort());
        c.setPage(q.getPage());
        c.setSize(q.getSize());
        return c;
    }
}
