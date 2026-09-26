package com.idlefish.trade.search.term.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.search.term.service.SearchTermService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索词运营接口（F-14.3，用户侧）：热搜榜、搜索历史、记录搜索。
 */
@RestController
@RequestMapping("/api/search/term")
public class SearchTermController {

    private final SearchTermService searchTermService;

    public SearchTermController(SearchTermService searchTermService) {
        this.searchTermService = searchTermService;
    }

    /** 热搜榜。 */
    @GetMapping("/hot")
    public Result<List<String>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchTermService.hotWords(limit));
    }

    /** 我的搜索历史（最近优先、去重）。 */
    @GetMapping("/history")
    public Result<List<String>> history(@RequestParam Long userId,
                                       @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchTermService.history(userId, limit));
    }

    /** 记录一次搜索（客户端在发起检索后调用）。 */
    @PostMapping("/record")
    public Result<Boolean> record(@RequestParam(required = false) Long userId,
                                 @RequestParam String word) {
        searchTermService.recordSearch(userId, word);
        return Result.ok(true);
    }
}
