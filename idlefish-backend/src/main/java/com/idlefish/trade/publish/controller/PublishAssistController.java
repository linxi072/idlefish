package com.idlefish.trade.publish.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.item.entity.AttrTemplate;
import com.idlefish.trade.publish.dto.DraftRequest;
import com.idlefish.trade.publish.dto.RecognizeRequest;
import com.idlefish.trade.publish.service.PublishAssistService;
import com.idlefish.trade.publish.vo.DraftSuggestion;
import com.idlefish.trade.publish.vo.PriceSuggestion;
import com.idlefish.trade.publish.vo.RecognizeSuggestion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序发布流程智能化用户端接口（U01）：图片识别 / 属性模板 / 估价 / 草稿。
 * AI 能力以可降级方式提供：AI 不可用时不阻塞发布（REQ-05）。
 */
@RestController
@RequestMapping("/api/publish-assist")
public class PublishAssistController {

    private final PublishAssistService publishAssistService;

    public PublishAssistController(PublishAssistService publishAssistService) {
        this.publishAssistService = publishAssistService;
    }

    /** REQ-01 图片识别建议类目（AI 不可用则 available=false，前端手工选择）。 */
    @PostMapping("/recognize")
    public Result<RecognizeSuggestion> recognize(@CurrentUser LoginUser user,
                                                @Valid @RequestBody RecognizeRequest req) {
        return Result.ok(publishAssistService.recognize(req));
    }

    /** REQ-03 按类目加载属性模板（真实数据）。 */
    @GetMapping("/attributes")
    public Result<List<AttrTemplate>> attributes(@CurrentUser LoginUser user, @RequestParam Long categoryId) {
        return Result.ok(publishAssistService.attributes(categoryId));
    }

    /** REQ-04 估价建议（参考值，单位分；AI/基准价均缺失时 hasData=false）。 */
    @GetMapping("/price")
    public Result<PriceSuggestion> price(@CurrentUser LoginUser user,
                                        @RequestParam Long categoryId,
                                        @RequestParam(required = false) Integer conditionLevel) {
        return Result.ok(publishAssistService.price(categoryId, conditionLevel));
    }

    /** REQ-02 草稿生成（AI 优先，否则规则模板兜底）。 */
    @PostMapping("/draft")
    public Result<DraftSuggestion> draft(@CurrentUser LoginUser user, @Valid @RequestBody DraftRequest req) {
        return Result.ok(publishAssistService.draft(req));
    }
}
