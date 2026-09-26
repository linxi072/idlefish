package com.idlefish.trade.search.term.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户搜索历史（F-14.3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_search_history")
public class SearchHistory extends BaseEntity {
    private Long userId;
    private String word;
}
