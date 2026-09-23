package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.item.vo.ItemVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于数据库的检索实现（模拟 Elasticsearch）。
 * 关键词对标题做 LIKE 模拟全文检索；无关键词时走兜底（最新在售）。
 * 仅当 idlefish.search.mock=true（默认）时生效。
 */
@Service
@ConditionalOnProperty(name = "idlefish.search.mock", havingValue = "true", matchIfMissing = true)
public class DbSearchServiceImpl implements SearchService {

    private final ItemMapper itemMapper;
    private final ItemService itemService;

    public DbSearchServiceImpl(ItemMapper itemMapper, ItemService itemService) {
        this.itemMapper = itemMapper;
        this.itemService = itemService;
    }

    @Override
    public IPage<ItemVO> search(ItemQueryDTO q) {
        if (q == null) {
            q = new ItemQueryDTO();
        }
        // 有有效关键词则走 DB 检索，否则兜底
        if (q.getKeyword() == null || q.getKeyword().isBlank()) {
            return fallback(q);
        }
        return itemService.buyerList(q);
    }

    @Override
    public List<String> suggest(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<>();
        w.select(Item::getTitle)
                .like(Item::getTitle, keyword)
                .eq(Item::getStatus, "on_sale")
                .groupBy(Item::getTitle)
                .last("LIMIT 10");
        return itemMapper.selectList(w).stream()
                .map(Item::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ItemVO> fallback(ItemQueryDTO q) {
        if (q == null) {
            q = new ItemQueryDTO();
        }
        return itemService.buyerList(q);
    }

    @Override
    public void indexItem(Item item) {
        // DB 实现：商品表即索引，无需额外写入
    }

    @Override
    public void removeItem(Long itemId) {
        // DB 实现：无需移除
    }
}
