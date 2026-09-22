package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;

import java.util.List;

/**
 * 搜索服务抽象（PRD §3.5 搜索）。
 * 生产接入 Elasticsearch；本地以 DB LIKE 检索 + 排序 + 联想 + 兜底实现，接口保持一致。
 */
public interface SearchService {

    /** 检索商品：关键词（模拟 ES 全文检索）、类目、成色、价格区间、排序、分页。 */
    IPage<ItemVO> search(ItemQueryDTO q);

    /** 搜索联想：返回匹配关键词的商品标题（最多 10 条），用于搜索框下拉。 */
    List<String> suggest(String keyword);

    /** 兜底检索：无关键词时返回在售最新商品，保证检索页不为空。 */
    IPage<ItemVO> fallback(ItemQueryDTO q);
}
