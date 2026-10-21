package com.idlefish.trade.search.enhance.service;

import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;

import java.util.List;

/**
 * 语义召回端口（ITER-F04 / REQ-02 融合召回、REQ-03 降级安全）。
 * <p>
 * 将「语义召回」抽象为端口，当前沙箱实现 {@link SynonymExpansionRecall} 以 F-14.3 同义词词典做
 * 扩展召回近似语义；真向量语义召回留作 ITER-T04 搜推服务化（届时新增实现并切换即可，不改动调用方）。
 * <p>
 * 契约约束：实现必须降级安全——不可用时返回空列表，<b>绝不抛异常或阻断主召回</b>。
 */
public interface SemanticRecallPort {

    /**
     * 语义相关商品召回。
     *
     * @param keyword 归一化后的关键词
     * @param base    原始检索条件（类目/价格/城市/分页等），用于约束召回范围
     * @return 召回商品列表（可能为空，但不得为 null，且不得抛异常）
     */
    List<ItemVO> recall(String keyword, ItemQueryDTO base);

    /**
     * 关键词扩展（同义词等），供零结果兜底「猜你想找」复用（默认仅返回原词）。
     *
     * @param keyword 归一化后的关键词
     * @return 扩展后的关键词集合（含原词）
     */
    default List<String> expansionKeywords(String keyword) {
        return java.util.List.of(keyword);
    }
}
