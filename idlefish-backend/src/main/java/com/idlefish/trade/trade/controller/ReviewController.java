package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.dto.ReviewSubmitDTO;
import com.idlefish.trade.trade.service.ReviewService;
import com.idlefish.trade.trade.vo.ReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评价接口（F-06）：提交 / 商品评价展示 / 我的评价 / 收到的评价。
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 提交评价（买家/卖家均可，按订单角色自动判定）。 */
    @PostMapping("/submit")
    public Result<Long> submit(@CurrentUser LoginUser loginUser, @Valid @RequestBody ReviewSubmitDTO dto) {
        return Result.ok(reviewService.submit(loginUser.getUserId(), dto));
    }

    /** 某商品的评价列表（展示）。 */
    @GetMapping("/item/{itemId}")
    public Result<List<ReviewVO>> listByItem(@PathVariable Long itemId) {
        return Result.ok(reviewService.listByItem(itemId));
    }

    /** 我发出的评价。 */
    @GetMapping("/my")
    public Result<List<ReviewVO>> my(@CurrentUser LoginUser loginUser) {
        return Result.ok(reviewService.myReviews(loginUser.getUserId()));
    }

    /** 我收到的评价（被评价方）。 */
    @GetMapping("/received")
    public Result<List<ReviewVO>> received(@CurrentUser LoginUser loginUser) {
        return Result.ok(reviewService.received(loginUser.getUserId()));
    }
}
