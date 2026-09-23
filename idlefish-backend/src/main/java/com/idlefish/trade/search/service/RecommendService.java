package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.item.vo.ItemVO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 首页推荐流（PRD §C2 规则兜底阶段）：
 * 同城优先 + 热度（浏览/收藏/点赞）加权排序。
 * 个性化推荐/冷启动/负反馈为后续阶段，当前以可运行的规则兜底实现。
 */
@Service
public class RecommendService {

    private final ItemMapper itemMapper;
    private final ItemService itemService;

    public RecommendService(ItemMapper itemMapper, ItemService itemService) {
        this.itemMapper = itemMapper;
        this.itemService = itemService;
    }

    /** 首页推荐流（同城优先 + 热度加权）。 */
    public Page<ItemVO> feed(String city, int page, int size) {
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
        List<ItemVO> sorted = all.stream()
                .sorted(Comparator
                        .comparingInt((Item it) -> it.getCity() != null && it.getCity().equals(targetCity) ? 0 : 1)
                        .thenComparingInt(it -> -(score(it))))
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

    /** 热度评分：浏览 + 收藏*5 + 点赞*3。 */
    private int score(Item it) {
        int view = it.getViewCount() == null ? 0 : it.getViewCount();
        int fav = it.getFavCount() == null ? 0 : it.getFavCount();
        int like = it.getLikeCount() == null ? 0 : it.getLikeCount();
        return view + fav * 5 + like * 3;
    }
}
