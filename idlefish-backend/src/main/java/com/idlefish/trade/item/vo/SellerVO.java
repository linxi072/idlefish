package com.idlefish.trade.item.vo;

import lombok.Data;

/**
 * 卖家视图（商品详情/列表契约对齐：小程序读取 item.seller.{id,creditScore,avatar,realNameVerified}）。
 */
@Data
public class SellerVO {

    private Long id;
    private String nickname;
    private String avatar;
    private Integer creditScore;
    private Integer realNameVerified; // 0 未认证 / 1 已认证
}
