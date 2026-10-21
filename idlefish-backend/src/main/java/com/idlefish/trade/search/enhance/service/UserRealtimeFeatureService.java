package com.idlefish.trade.search.enhance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.cache.CacheService;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户实时兴趣/意图特征（ITER-F04 搜索推荐深化 / REQ-01 行为 60s 内更新实时兴趣、REQ-09 搜索意图反哺推荐）。
 * <p>
 * 基于 Redis 短窗口（默认 {@value #WINDOW_SECONDS}s）缓存「最近行为沉淀的类目兴趣权重」与「最近搜索意图词」，
 * 供：
 * ① 搜索融合排序的实时加权（REQ-01：行为立刻影响当次/后续检索排序）；
 * ② 推荐流覆盖长期画像（REQ-09：搜索意图反哺首页推荐）。
 * <p>
 * 所有写操作 best-effort：Redis 不可用时静默降级，绝不抛异常阻断主流程；读取缺失/异常均返回空集合。
 */
@Service
public class UserRealtimeFeatureService {

    /** 实时特征窗口：60s 内行为生效（REQ-01）。 */
    static final long WINDOW_SECONDS = 60L;
    private static final String RT_INTEREST_KEY = "rt:interest:";
    private static final String RT_INTENT_KEY = "rt:intent:";
    private static final int MAX_INTENTS = 10;

    private final CacheService cache;
    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper;
    private final MetricsRegistry metrics;

    public UserRealtimeFeatureService(CacheService cache, ObjectMapper objectMapper,
                                      ItemMapper itemMapper, MetricsRegistry metrics) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.itemMapper = itemMapper;
        this.metrics = metrics;
    }

    /**
     * 记录一次用户行为（REQ-01/09）。
     *
     * @param userId 用户 ID（null 不记录）
     * @param event  事件类型：search（记意图）/ view_item·favorite·order_create·pay（记类目兴趣）
     * @param bizId  业务主键：search 时为关键词；其余为商品 ID（字符串）
     */
    public void recordBehavior(Long userId, String event, String bizId) {
        if (userId == null || event == null) {
            return;
        }
        try {
            if ("search".equals(event)) {
                recordIntent(userId, bizId);
            } else {
                recordInterest(userId, event, bizId);
            }
            metrics.increment("realtime.feature.record");
        } catch (RuntimeException ex) {
            // best-effort：缓存/DB 异常不影响主流程
            metrics.increment("realtime.feature.error");
        }
    }

    /** 读取实时类目兴趣权重（窗口内；过期/缺失/异常返回空）。 */
    public Map<Long, Double> getRealtimeInterest(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        try {
            String json = (String) cache.get(RT_INTEREST_KEY + userId);
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            Map<Long, Double> map = objectMapper.readValue(json, new TypeReference<Map<Long, Double>>() {});
            return map == null ? Collections.emptyMap() : map;
        } catch (RuntimeException | IOException ex) {
            return Collections.emptyMap();
        }
    }

    /** 读取最近搜索意图词（最新在前，最多 {@value #MAX_INTENTS} 个）。 */
    public List<String> getRecentIntents(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        try {
            String json = (String) cache.get(RT_INTENT_KEY + userId);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (RuntimeException | IOException ex) {
            return Collections.emptyList();
        }
    }

    private void recordInterest(Long userId, String event, String bizId) {
        Long itemId = parseLong(bizId);
        if (itemId == null) {
            return;
        }
        Item item = itemMapper.selectById(itemId);
        if (item == null || item.getCategoryId() == null) {
            return;
        }
        double w = eventWeight(event);
        if (w <= 0) {
            return;
        }
        // 用可变副本承接（getRealtimeInterest 缺失/异常时返回不可变空 Map，不可直接 merge）
        Map<Long, Double> weights = new HashMap<>(getRealtimeInterest(userId));
        weights.merge(item.getCategoryId(), w, Double::sum);
        cache.put(RT_INTEREST_KEY + userId, serialize(weights), WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    private void recordIntent(Long userId, String word) {
        if (word == null || word.isBlank()) {
            return;
        }
        String norm = word.trim();
        List<String> intents = getRecentIntents(userId);
        List<String> updated = new ArrayList<>();
        updated.add(norm); // 最新在前
        for (String s : intents) {
            if (!s.equals(norm) && updated.size() < MAX_INTENTS) {
                updated.add(s);
            }
        }
        cache.put(RT_INTENT_KEY + userId, serialize(updated), WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (RuntimeException | IOException ex) {
            return "{}";
        }
    }

    private Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 埋点事件 → 兴趣权重（浏览1 / 收藏3 / 下单5 / 支付4；其余 0）。 */
    static double eventWeight(String event) {
        if (event == null) {
            return 0.0;
        }
        switch (event) {
            case "view_item":    return 1.0;
            case "favorite":     return 3.0;
            case "order_create": return 5.0;
            case "pay":          return 4.0;
            default:             return 0.0;
        }
    }
}
