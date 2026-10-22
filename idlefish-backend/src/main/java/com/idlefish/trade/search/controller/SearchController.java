package com.idlefish.trade.search.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.serving.SearchServingFacade;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 搜索接口（免登录，WebConfig 已放行 /api/search/**）。
 * PRD §3.5：关键词检索、筛选、排序、联想、兜底。
 * <p>
 * ITER-T04：首页推荐流（/recommend）改经 {@link SearchServingFacade} 路由（双模式 + 熔断降级 +
 * 资源隔离），其余纯 ES 检索端点保持直连（薄透传，无融合/打分开销）。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchServingFacade servingFacade;

    public SearchController(SearchService searchService, SearchServingFacade servingFacade) {
        this.searchService = searchService;
        this.servingFacade = servingFacade;
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

    /** 聚合筛选（F-14.1）：在检索条件基础上返回类目/成色/城市三维度计数，供前端筛选栏展示。 */
    @GetMapping("/facets")
    public Result<Map<String, Map<String, Long>>> facets(ItemQueryDTO q) {
        return Result.ok(searchService.facets(q));
    }

    /**
     * 首页推荐流（F-14.2 个性化：同城优先 + 热度衰减 + 行为加权 + 冷启动保量）。
     * <p>ITER-T04：经搜推门面路由——内联模式进程内执行；远程模式调用独立搜推服务，
     * 失败自动回落内联/兜底，绝不影响主流程（REQ-03）。</p>
     */
    @GetMapping("/recommend")
    public Result<IPage<ItemVO>> recommend(@RequestParam(required = false) String city,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(servingFacade.feed(city, userId, page, size).getData());
    }
}
