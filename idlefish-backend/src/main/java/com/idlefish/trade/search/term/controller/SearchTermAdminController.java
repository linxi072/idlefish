package com.idlefish.trade.search.term.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.search.term.entity.SearchSynonym;
import com.idlefish.trade.search.term.service.SearchTermService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索词运营后台接口（F-14.3）：屏蔽词、同义词、热搜词状态管理。
 */
@RestController
@RequestMapping("/api/admin/search-term")
public class SearchTermAdminController {

    private final SearchTermService searchTermService;

    public SearchTermAdminController(SearchTermService searchTermService) {
        this.searchTermService = searchTermService;
    }

    /** 新增屏蔽词。 */
    @PostMapping("/block")
    public Result<Boolean> block(@RequestParam String word) {
        searchTermService.addBlockWord(word);
        return Result.ok(true);
    }

    /** 移除屏蔽词。 */
    @PostMapping("/unblock")
    public Result<Boolean> unblock(@RequestParam String word) {
        searchTermService.removeBlockWord(word);
        return Result.ok(true);
    }

    /** 新增同义词。 */
    @PostMapping("/synonym")
    public Result<Boolean> synonym(@RequestParam String word, @RequestParam String synonym) {
        searchTermService.addSynonym(word, synonym);
        return Result.ok(true);
    }

    /** 同义词词典列表。 */
    @GetMapping("/synonyms")
    public Result<List<SearchSynonym>> synonyms() {
        return Result.ok(searchTermService.synonyms());
    }

    /** 设置热搜词状态（ENABLED/BLOCKED）。 */
    @PostMapping("/hotword/status")
    public Result<Boolean> hotWordStatus(@RequestParam String word, @RequestParam String status) {
        searchTermService.setHotWordStatus(word, status);
        return Result.ok(true);
    }
}
