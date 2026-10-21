package com.idlefish.trade.search.enhance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.enhance.dto.BucketResult;
import com.idlefish.trade.search.enhance.entity.AbExperiment;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.term.service.SearchTermService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 搜索增强编排服务单测（ITER-F04 / REQ-02/03/04/06/08/09）。
 * Mock 全部外部依赖，验证：屏蔽词拦截、融合排序、语义降级、零结果兜底、实时意图反哺、AB 偏移读取。
 */
class SearchEnhanceServiceTest {

    private final SearchService searchService = mock(SearchService.class);
    private final SearchTermService termService = mock(SearchTermService.class);
    private final SemanticRecallPort semanticRecall = mock(SemanticRecallPort.class);
    private final UserRealtimeFeatureService featureService = mock(UserRealtimeFeatureService.class);
    private final AbExperimentService abService = mock(AbExperimentService.class);
    private final MetricsRegistry metrics = mock(MetricsRegistry.class);
    private final SearchEnhanceService service =
            new SearchEnhanceService(searchService, termService, semanticRecall, featureService, abService, metrics);

    private ItemVO item(long id, String city) {
        ItemVO v = new ItemVO();
        v.setId(id);
        v.setViewCount(10);
        v.setFavCount(2);
        v.setLikeCount(1);
        v.setCategoryId(7L);
        v.setCity(city);
        return v;
    }

    private IPage<ItemVO> pageOf(List<ItemVO> items) {
        Page<ItemVO> p = new Page<>(1, 20, items.size());
        p.setRecords(items);
        return p;
    }

    @Test
    @DisplayName("屏蔽词命中：直接返回空页，不报错、不调用检索")
    void blockedKeyword() {
        when(termService.isBlocked("违禁品")).thenReturn(true);
        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("违禁品");
        IPage<ItemVO> r = service.enhancedSearch(q, 1001L);
        assertTrue(r.getRecords().isEmpty());
        verify(searchService, times(0)).search(any());
    }

    @Test
    @DisplayName("正常检索：关键词主召回 + 同义词扩展融合，按融合分排序")
    void fusionRanking() {
        when(termService.isBlocked(anyString())).thenReturn(false);
        ItemVO primary = item(1, "北京");
        ItemVO semantic = item(2, "上海");
        when(searchService.search(any(ItemQueryDTO.class))).thenReturn(pageOf(List.of(primary)));
        when(semanticRecall.recall(anyString(), any(ItemQueryDTO.class))).thenReturn(List.of(semantic));
        when(featureService.getRealtimeInterest(any())).thenReturn(Map.of());

        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("手机");
        q.setCity("北京");
        IPage<ItemVO> r = service.enhancedSearch(q, 1002L);
        assertEquals(2, r.getRecords().size());
        assertEquals(1L, r.getRecords().get(0).getId()); // 主召回同城+高热度领先
    }

    @Test
    @DisplayName("语义召回异常降级：不影响主召回结果（REQ-03 不空结果）")
    void semanticDegrade() {
        when(termService.isBlocked(anyString())).thenReturn(false);
        ItemVO primary = item(1, "北京");
        when(searchService.search(any())).thenReturn(pageOf(List.of(primary)));
        when(semanticRecall.recall(anyString(), any())).thenThrow(new RuntimeException("向量服务挂了"));
        when(featureService.getRealtimeInterest(any())).thenReturn(Map.of());

        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("手机");
        IPage<ItemVO> r = service.enhancedSearch(q, 1003L);
        assertEquals(1, r.getRecords().size());
        assertEquals(1L, r.getRecords().get(0).getId());
    }

    @Test
    @DisplayName("零结果兜底：主召回+语义均空 → 调用「猜你想找」")
    void zeroResultFallback() {
        when(termService.isBlocked(anyString())).thenReturn(false);
        // 主召回/兜底按关键词区分：冷门词→空，热搜词→兜底结果
        when(searchService.search(any())).thenAnswer(inv -> {
            ItemQueryDTO q = inv.getArgument(0);
            String kw = q.getKeyword();
            if (kw != null && (kw.contains("iPhone") || kw.contains("华为") || kw.contains("智能"))) {
                return pageOf(List.of(item(99, "广州")));
            }
            return pageOf(List.of());
        });
        when(semanticRecall.recall(anyString(), any())).thenReturn(List.of());
        when(featureService.getRealtimeInterest(any())).thenReturn(Map.of());
        when(termService.hotWords(anyInt())).thenReturn(List.of("iPhone", "华为"));

        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("冷门词");
        IPage<ItemVO> r = service.enhancedSearch(q, 1004L);
        assertFalse(r.getRecords().isEmpty());
        verify(metrics).increment("search.enhance.zerofallback");
    }

    @Test
    @DisplayName("AB 实验偏移：实验组生效时读取 recallBoost 且召回融合不丢语义结果")
    void abRecallBoost() {
        when(termService.isBlocked(anyString())).thenReturn(false);
        ItemVO low = item(1, "北京"); low.setViewCount(1);   // 低热度
        ItemVO high = item(2, "北京"); high.setViewCount(100); // 高热度
        when(searchService.search(any())).thenReturn(pageOf(List.of(low)));
        when(semanticRecall.recall(anyString(), any())).thenReturn(List.of(high));
        when(featureService.getRealtimeInterest(any())).thenReturn(Map.of());

        AbExperiment exp = new AbExperiment();
        exp.setId(1L);
        exp.setName("boost");
        exp.setStatus("RUNNING");
        exp.setTreatmentPercent(100);
        exp.setStrategyParams("{\"recallBoost\":2.0}");
        when(abService.currentEffective()).thenReturn(exp);
        when(abService.assignTo(eq(exp), any())).thenReturn(
                new BucketResult(1L, "boost", "TREATMENT", true, 100,
                        Map.<String, Object>of("recallBoost", 2.0)));

        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("手机");
        IPage<ItemVO> r = service.enhancedSearch(q, 1005L);
        assertEquals(2, r.getRecords().size());
        assertEquals(2L, r.getRecords().get(0).getId()); // 高热度优先
        verify(abService).currentEffective();
        verify(abService).assignTo(eq(exp), any());
    }

    @Test
    @DisplayName("实时意图反哺：检索时记录 search 行为（REQ-09）")
    void recordSearchIntent() {
        when(termService.isBlocked(anyString())).thenReturn(false);
        when(searchService.search(any())).thenReturn(pageOf(List.of()));
        when(semanticRecall.recall(anyString(), any())).thenReturn(List.of());
        when(featureService.getRealtimeInterest(any())).thenReturn(Map.of());

        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("篮球鞋");
        service.enhancedSearch(q, 1006L);
        verify(featureService).recordBehavior(eq(1006L), eq("search"), eq("篮球鞋"));
    }

    @Test
    @DisplayName("猜你想找：合并同义词扩展与热搜兜底")
    void guessYouWant() {
        when(semanticRecall.expansionKeywords("手机")).thenReturn(List.of("手机", "智能手机"));
        when(termService.hotWords(anyInt())).thenReturn(List.of("iPhone", "华为"));
        when(searchService.search(any())).thenReturn(pageOf(List.of(item(1, "a"), item(2, "b"))));

        List<ItemVO> r = service.guessYouWant("手机", 10);
        assertFalse(r.isEmpty());
        verify(termService).hotWords(anyInt());
    }
}
