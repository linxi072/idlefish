package com.idlefish.trade.search.term.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 搜索同义词词典（F-14.3）：用于联想与召回扩展。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_search_synonym")
public class SearchSynonym extends BaseEntity {
    private String word;
    private String synonym;
}
