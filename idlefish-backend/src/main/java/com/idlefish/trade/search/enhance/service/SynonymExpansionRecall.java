package com.idlefish.trade.search.enhance.service;

import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.term.SearchTermFilter;
import com.idlefish.trade.search.term.entity.SearchSynonym;
import com.idlefish.trade.search.term.service.SearchTermService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 同义词扩展召回（ITER-F04 沙箱可运行子集的「语义近似」实现 / REQ-02/03）。
 * <p>
 * 复用 F-14.3 同义词词典，将关键词与其同义词（双向匹配）作为多路召回，模拟语义扩展召回效果；
 * 真向量语义召回由 ITER-T04 搜推服务化承接。全程 try/catch 降级：任一路召回失败返回空，
 * 绝不影响关键词主召回与整体响应（REQ-03）。
 */
@Service
public class SynonymExpansionRecall implements SemanticRecallPort {

    private final SearchService searchService;
    private final SearchTermService termService;

    public SynonymExpansionRecall(SearchService searchService, SearchTermService termService) {
        this.searchService = searchService;
        this.termService = termService;
    }

    @Override
    public List<ItemVO> recall(String keyword, ItemQueryDTO base) {
        try {
            Set<String> expansion = new LinkedHashSet<>(expansionKeywords(keyword));
            int size = Math.max(base.getSize() == null ? 20 : base.getSize(), 10);
            List<ItemVO> all = new ArrayList<>();
            for (String kw : expansion) {
                ItemQueryDTO q = cloneBase(base);
                q.setKeyword(kw);
                q.setPage(1);
                q.setSize(size);
                try {
                    var page = searchService.search(q);
                    if (page != null && page.getRecords() != null) {
                        all.addAll(page.getRecords());
                    }
                } catch (RuntimeException ex) {
                    // 单路召回失败不影响整体（降级安全）
                }
            }
            return SearchFusion.dedupe(all);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    @Override
    public List<String> expansionKeywords(String keyword) {
        Set<String> set = new LinkedHashSet<>();
        String norm = SearchTermFilter.normalize(keyword);
        if (norm.isEmpty()) {
            return List.of();
        }
        set.add(norm);
        List<SearchSynonym> synonyms = termService.synonyms();
        if (synonyms != null) {
            for (SearchSynonym s : synonyms) {
                String w = SearchTermFilter.normalize(s.getWord());
                String syn = SearchTermFilter.normalize(s.getSynonym());
                if (norm.equals(w)) {
                    set.add(syn);
                } else if (norm.equals(syn)) {
                    set.add(w);
                }
            }
        }
        return new ArrayList<>(set);
    }

    private ItemQueryDTO cloneBase(ItemQueryDTO base) {
        ItemQueryDTO q = new ItemQueryDTO();
        q.setCategoryId(base.getCategoryId());
        q.setCity(base.getCity());
        q.setProvince(base.getProvince());
        q.setMinPrice(base.getMinPrice());
        q.setMaxPrice(base.getMaxPrice());
        q.setSort(base.getSort());
        q.setPage(base.getPage());
        q.setSize(base.getSize());
        return q;
    }
}
