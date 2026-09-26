package com.idlefish.trade.search.term.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.search.term.SearchTermFilter;
import com.idlefish.trade.search.term.entity.SearchBlockWord;
import com.idlefish.trade.search.term.entity.SearchHistory;
import com.idlefish.trade.search.term.entity.SearchHotWord;
import com.idlefish.trade.search.term.entity.SearchSynonym;
import com.idlefish.trade.search.term.mapper.SearchBlockWordMapper;
import com.idlefish.trade.search.term.mapper.SearchHistoryMapper;
import com.idlefish.trade.search.term.mapper.SearchHotWordMapper;
import com.idlefish.trade.search.term.mapper.SearchSynonymMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索词运营服务（F-14.3）：热搜榜、搜索历史、屏蔽词、同义词词典。
 * 记录/热搜累加走事务；屏蔽词命中则不入库、不入热搜。
 */
@Service
public class SearchTermService {

    private final SearchHotWordMapper hotMapper;
    private final SearchHistoryMapper historyMapper;
    private final SearchBlockWordMapper blockMapper;
    private final SearchSynonymMapper synonymMapper;
    private final MetricsRegistry metrics;

    public SearchTermService(SearchHotWordMapper hotMapper, SearchHistoryMapper historyMapper,
                             SearchBlockWordMapper blockMapper, SearchSynonymMapper synonymMapper,
                             MetricsRegistry metrics) {
        this.hotMapper = hotMapper;
        this.historyMapper = historyMapper;
        this.blockMapper = blockMapper;
        this.synonymMapper = synonymMapper;
        this.metrics = metrics;
    }

    /** 记录一次搜索：写历史 + 热搜累加（屏蔽词跳过）。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordSearch(Long userId, String rawWord) {
        String word = SearchTermFilter.normalize(rawWord);
        if (word.isEmpty() || isBlocked(word)) {
            return;
        }
        SearchHistory h = new SearchHistory();
        h.setUserId(userId);
        h.setWord(word);
        historyMapper.insert(h);

        SearchHotWord hw = hotMapper.selectOne(
                new LambdaQueryWrapper<SearchHotWord>().eq(SearchHotWord::getWord, word));
        if (hw == null) {
            hw = new SearchHotWord();
            hw.setWord(word);
            hw.setHeat(1);
            hw.setStatus("ENABLED");
            hotMapper.insert(hw);
        } else {
            hw.setHeat((hw.getHeat() == null ? 0 : hw.getHeat()) + 1);
            hotMapper.updateById(hw);
        }
        metrics.increment("search.term.record");
    }

    /** 热搜榜（仅 ENABLED，按热度倒序，限制条数）。 */
    public List<String> hotWords(int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }
        List<SearchHotWord> list = hotMapper.selectList(new LambdaQueryWrapper<SearchHotWord>()
                .eq(SearchHotWord::getStatus, "ENABLED")
                .orderByDesc(SearchHotWord::getHeat)
                .last("LIMIT " + limit));
        return list.stream().map(SearchHotWord::getWord).collect(Collectors.toList());
    }

    /** 我的搜索历史（最近优先、去重保序）。 */
    public List<String> history(Long userId, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }
        List<SearchHistory> list = historyMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreatedAt)
                .last("LIMIT " + limit));
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        list.forEach(h -> distinct.add(h.getWord()));
        return new ArrayList<>(distinct);
    }

    /** 全部屏蔽词（归一化后）。 */
    public List<String> blockWords() {
        return blockMapper.selectList(new LambdaQueryWrapper<SearchBlockWord>())
                .stream().map(SearchBlockWord::getWord).collect(Collectors.toList());
    }

    /** 是否命中屏蔽词。 */
    public boolean isBlocked(String word) {
        String w = SearchTermFilter.normalize(word);
        if (w.isEmpty()) {
            return false;
        }
        return blockMapper.selectOne(
                new LambdaQueryWrapper<SearchBlockWord>().eq(SearchBlockWord::getWord, w)) != null;
    }

    // ===== 运营后台 =====

    /** 新增屏蔽词（并同步将该词热搜置为 BLOCKED）。 */
    @Transactional(rollbackFor = Exception.class)
    public void addBlockWord(String rawWord) {
        String word = SearchTermFilter.normalize(rawWord);
        if (word.isEmpty()) {
            return;
        }
        if (blockMapper.selectOne(new LambdaQueryWrapper<SearchBlockWord>().eq(SearchBlockWord::getWord, word)) != null) {
            return;
        }
        SearchBlockWord b = new SearchBlockWord();
        b.setWord(word);
        blockMapper.insert(b);
        SearchHotWord hw = hotMapper.selectOne(
                new LambdaQueryWrapper<SearchHotWord>().eq(SearchHotWord::getWord, word));
        if (hw != null) {
            hw.setStatus("BLOCKED");
            hotMapper.updateById(hw);
        }
    }

    /** 移除屏蔽词（并恢复该词热搜为 ENABLED）。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeBlockWord(String rawWord) {
        String word = SearchTermFilter.normalize(rawWord);
        blockMapper.delete(new LambdaQueryWrapper<SearchBlockWord>().eq(SearchBlockWord::getWord, word));
        SearchHotWord hw = hotMapper.selectOne(
                new LambdaQueryWrapper<SearchHotWord>().eq(SearchHotWord::getWord, word));
        if (hw != null) {
            hw.setStatus("ENABLED");
            hotMapper.updateById(hw);
        }
    }

    /** 新增同义词。 */
    @Transactional(rollbackFor = Exception.class)
    public void addSynonym(String word, String synonym) {
        SearchSynonym s = new SearchSynonym();
        s.setWord(SearchTermFilter.normalize(word));
        s.setSynonym(SearchTermFilter.normalize(synonym));
        synonymMapper.insert(s);
    }

    /** 同义词词典。 */
    public List<SearchSynonym> synonyms() {
        return synonymMapper.selectList(new LambdaQueryWrapper<SearchSynonym>());
    }

    /** 设置热搜词状态（ENABLED/BLOCKED）。 */
    @Transactional(rollbackFor = Exception.class)
    public void setHotWordStatus(String rawWord, String status) {
        String word = SearchTermFilter.normalize(rawWord);
        SearchHotWord hw = hotMapper.selectOne(
                new LambdaQueryWrapper<SearchHotWord>().eq(SearchHotWord::getWord, word));
        if (hw != null) {
            hw.setStatus(status);
            hotMapper.updateById(hw);
        }
    }
}
