package com.idlefish.trade.favorite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏（PRD §4.2 收藏夹）。一对用户-商品唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_favorite")
public class Favorite extends BaseEntity {

    private Long userId;
    private Long itemId;
}
