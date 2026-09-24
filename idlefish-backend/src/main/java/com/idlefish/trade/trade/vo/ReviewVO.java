package com.idlefish.trade.trade.vo;

import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 评价展示 VO（F-06）。匿名评价隐藏评价人昵称（此处仅透出 ID，前端按 anonymous 决定是否展示）。
 */
@Data
public class ReviewVO {

    private Long id;
    private Long itemId;
    private Long reviewerId;
    private Long targetId;
    private String role;
    private Integer rating;
    private String content;
    private Integer anonymous;
    private String createdAt;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ReviewVO from(com.idlefish.trade.trade.entity.Review r) {
        ReviewVO v = new ReviewVO();
        v.setId(r.getId());
        v.setItemId(r.getItemId());
        v.setReviewerId(r.getReviewerId());
        v.setTargetId(r.getTargetId());
        v.setRole(r.getRole());
        v.setRating(r.getRating());
        v.setContent(r.getContent());
        v.setAnonymous(r.getAnonymous());
        v.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(FMT));
        return v;
    }
}
