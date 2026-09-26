package com.idlefish.trade.search.term.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 搜索屏蔽词（F-14.3）：命中则不下发热搜、不进搜索历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_search_block_word")
public class SearchBlockWord extends BaseEntity {
    /** 屏蔽词（唯一，大小写不敏感存储归一化后的值） */
    private String word;
}
