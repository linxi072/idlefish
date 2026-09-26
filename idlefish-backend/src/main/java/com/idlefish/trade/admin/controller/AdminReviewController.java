package com.idlefish.trade.admin.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评价管理（F-06）。
 * 待审评价列表 / 通过 / 驳回，通过评价触发被评价方信用重算（由 ReviewService 内部完成）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 待审核评价列表。 */
    @GetMapping("/reviews")
    public Result<List<com.idlefish.trade.trade.vo.ReviewVO>> reviews(@CurrentAdmin AdminUser admin) {
        return Result.ok(reviewService.pendingList());
    }

    /** 审核通过评价（触发被评价方信用重算）。 */
    @PostMapping("/reviews/{id}/approve")
    public Result<Void> approveReview(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        reviewService.approve(id);
        return Result.ok();
    }

    /** 审核驳回评价。 */
    @PostMapping("/reviews/{id}/reject")
    public Result<Void> rejectReview(@CurrentAdmin AdminUser admin, @PathVariable Long id,
                                     @RequestParam(required = false) String reason) {
        reviewService.reject(id, reason);
        return Result.ok();
    }
}
