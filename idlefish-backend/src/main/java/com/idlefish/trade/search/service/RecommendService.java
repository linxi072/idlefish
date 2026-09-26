package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 首页推荐流（F-14.2 个性化推荐）：
 * 同城优先 + 热度（浏览/收藏/点赞，按上架时间半衰期衰减） + 行为加权（用户历史浏览/收藏/下单/支付沉淀的类目兴趣）
 * + 冷启动保量（上架不久的新品固定加分确保进入推荐流）。
 * 打分逻辑沉淀于纯函数 {@link RecommendScorer}，本服务仅负责取数与组装。
 */
@Service
public class RecommendService {

    private final ItemMapper itemMapper;
    private final ItemService itemService;
    private final TrackEventMapper trackEventMapper;

    public RecommendService(ItemMapper itemMapper, ItemService itemService, TrackEventMapper trackEventMapper) {
        this.itemMapper = itemMapper;
        this.itemService = itemService;
        this.trackEventMapper = trackEventMapper;
    }

    /** 首页推荐流（兼容旧调用，无用户上下文）。 */
    public Page<ItemVO> feed(String city, int page, int size) {
        return feed(city, null, page, size);
    }

    /** 首页推荐流（同城优先 + 热度衰减 + 行为加权 + 冷启动保量）。 */
    public Page<ItemVO> feed(String city, Long userId, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 50) {
            size = 10;
        }
        List<Item> all = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, "onsale")
                .eq(Item::getAuditStatus, "passed"));

        final String targetCity = (city == null ? "" : city).trim();
        LocalDateTime now = LocalDateTime.now();
        // 用户兴趣向量（类目权重），未登录则为空
        Map<Long, Double> catWeights = buildInterestWeights(userId);

        List<ItemVO> sorted = all.stream()
                .sorted(Comparator.comparingDouble((Item it) -> -score(it, targetCity, now, catWeights)))
                .map(itemService::toVO)
                .collect(Collectors.toList());

        long total = sorted.size();
        int from = Math.min((page - 1) * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        List<ItemVO> records = sorted.subList(from, to);

        Page<ItemVO> result = new Page<>(page, size, total);
        result.setRecords(records);
        return result;
    }

    /** 单品综合推荐分：热度 ×(1+兴趣) + 同城加成 + 冷启动保量。 */
    private double score(Item it, String targetCity, LocalDateTime now, Map<Long, Double> catWeights) {
        double heat = RecommendScorer.heatScore(it, now);
        double interest = RecommendScorer.interestBoost(it, catWeights);
        boolean sameCity = it.getCity() != null && it.getCity().equals(targetCity);
        double cold = RecommendScorer.coldStartBoost(it, now);
        return RecommendScorer.finalScore(heat, interest, sameCity, cold);
    }

    /**
     * 由用户埋点构建类目兴趣向量：事件 bizId → item → categoryId，权重累加。
     * 关联商品批量加载，避免逐条 N+1。
     */
    private Map<Long, Double> buildInterestWeights(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        List<TrackEvent> events = trackEventMapper.selectList(new LambdaQueryWrapper<TrackEvent>()
                .eq(TrackEvent::getUserId, userId)
                .in(TrackEvent::getEvent, "view_item", "favorite", "order_create", "pay"));
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> itemIds = events.stream()
                .map(e -> toLong(e.getBizId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Item> itemMap = itemIds.isEmpty() ? Collections.emptyMap()
                : itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, i -> i, (a, b) -> a));

        Map<Long, Double> weights = new HashMap<>();
        for (TrackEvent e : events) {
            Long itemId = toLong(e.getBizId());
            Item it = itemId == null ? null : itemMap.get(itemId);
            if (it == null || it.getCategoryId() == null) {
                continue;
            }
            double w = RecommendScorer.eventWeight(e.getEvent());
            weights.merge(it.getCategoryId(), w, Double::sum);
        }
        return weights;
    }

    private Long toLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
