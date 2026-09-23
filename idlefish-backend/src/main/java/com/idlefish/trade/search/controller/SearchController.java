package com.idlefish.trade.search.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.service.RecommendService;
import com.idlefish.trade.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索接口（免登录，WebConfig 已放行 /api/search/**）。
 * PRD §3.5：关键词检索、筛选、排序、联想、兜底。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final RecommendService recommendService;

    public SearchController(SearchService searchService, RecommendService recommendService) {
        this.searchService = searchService;
        this.recommendService = recommendService;
    }

    /** 商品检索。 */
    @GetMapping("/query")
    public Result<IPage<ItemVO>> query(ItemQueryDTO q) {
        return Result.ok(searchService.search(q));
    }

    /** 搜索联想。 */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam(required = false) String keyword) {
        return Result.ok(searchService.suggest(keyword));
    }

    /** 兜底检索（最新在售）。 */
    @GetMapping("/fallback")
    public Result<IPage<ItemVO>> fallback(ItemQueryDTO q) {
        return Result.ok(searchService.fallback(q));
    }

    /** 首页推荐流（同城优先 + 热度加权，PRD §C2）。 */
    @GetMapping("/recommend")
    public Result<IPage<ItemVO>> recommend(@RequestParam(required = false) String city,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(recommendService.feed(city, page, size));
    }
}
