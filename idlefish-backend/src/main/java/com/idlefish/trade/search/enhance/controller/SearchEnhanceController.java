package com.idlefish.trade.search.enhance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.enhance.service.SearchEnhanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索增强接口（免登录，WebConfig 已放行 /api/search/**）。
 * <p>
 * ITER-F04 搜索推荐深化 / REQ-02/03/04/06/08/09：
 * ① 增强检索（屏蔽词拦截 + 关键词/同义词融合 + 实时加权 + AB 偏移 + 零结果兜底）；
 * ② 零结果兜底「猜你想找」；
 * ③ 实时行为回传（更新 60s 内实时兴趣，反哺搜索与推荐）。
 * <p>
 * 全部链路降级安全：任一外部依赖异常均降级为安全结果，绝不阻断主流程。
 */
@RestController
@RequestMapping("/api/search/enhance")
public class SearchEnhanceController {

    private final SearchEnhanceService searchEnhanceService;

    public SearchEnhanceController(SearchEnhanceService searchEnhanceService) {
        this.searchEnhanceService = searchEnhanceService;
    }

    /**
     * 增强检索主入口。
     * 屏蔽词命中（REQ-08）直接返回空、不报错、不记录；零结果（REQ-04）自动兜底「猜你想找」。
     *
     * @param q      检索条件（关键词/类目/价格/城市/排序/分页，绑定自 query 参数）
     * @param userId 用户 ID（可空：空则不做实时加权与分桶）
     */
    @GetMapping("/query")
    public Result<IPage<ItemVO>> query(ItemQueryDTO q,
                                       @RequestParam(required = false) Long userId) {
        return Result.ok(searchEnhanceService.enhancedSearch(q, userId));
    }

    /**
     * 零结果兜底「猜你想找」（REQ-04）。
     *
     * @param keyword 归一化关键词（空则纯热搜兜底）
     * @param limit   返回条数上限
     */
    @GetMapping("/guess")
    public Result<List<ItemVO>> guess(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchEnhanceService.guessYouWant(keyword, limit));
    }

    /**
     * 实时行为回传（REQ-01）：客户端在 view_item / favorite / order_create / pay 时调用，
     * 立即更新 60s 内实时兴趣，并反哺搜索排序与首页推荐流（REQ-09）。
     *
     * @param userId 用户 ID
     * @param event  事件类型：search/view_item/favorite/order_create/pay
     * @param bizId  search 时为关键词，其余为商品 ID（字符串）
     */
    @PostMapping("/behavior")
    public Result<Boolean> behavior(@RequestParam Long userId,
                                    @RequestParam String event,
                                    @RequestParam String bizId) {
        searchEnhanceService.recordBehavior(userId, event, bizId);
        return Result.ok(true);
    }
}
