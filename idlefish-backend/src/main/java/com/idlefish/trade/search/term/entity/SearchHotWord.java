package com.idlefish.trade.search.term.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 热搜词（F-14.3）：记录用户搜索热度，运营可置为屏蔽（BLOCKED 不下发）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_search_hot_word")
public class SearchHotWord extends BaseEntity {
    private String word;
    /** 热度计数 */
    private Integer heat;
    /** ENABLED 正常 / BLOCKED 屏蔽（运营下架） */
    private String status;
}
