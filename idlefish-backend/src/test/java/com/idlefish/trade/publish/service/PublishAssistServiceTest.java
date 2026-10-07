package com.idlefish.trade.publish.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.AttrTemplate;
import com.idlefish.trade.item.service.AttrTemplateService;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.publish.AiAssistGateway;
import com.idlefish.trade.publish.ReferencePriceProvider;
import com.idlefish.trade.publish.dto.DraftRequest;
import com.idlefish.trade.publish.dto.RecognizeRequest;
import com.idlefish.trade.publish.vo.DraftSuggestion;
import com.idlefish.trade.publish.vo.PriceSuggestion;
import com.idlefish.trade.publish.vo.RecognizeSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 小程序发布流程智能化服务单测（U01）：聚焦可降级能力。
 * AI 网关与基准价提供方均通过 Optional 注入，默认 empty → 规则兜底 / 显式无数据（REQ-05 不阻塞发布）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishAssistServiceTest {

    @Mock private CategoryService categoryService;
    @Mock private AttrTemplateService attrTemplateService;
    @Mock private MetricsRegistry metrics;

    /** 默认无 AI、无基准价（规则兜底 / 显式无数据场景）。 */
    private PublishAssistService service;

    @BeforeEach
    void setUp() {
        service = new PublishAssistService(categoryService, attrTemplateService, metrics,
                Optional.empty(), Optional.empty());
    }

    // ---------- REQ-01 图片识别 ----------

    @Test
    void recognize_noAi_unavailable() {
        RecognizeRequest req = new RecognizeRequest();
        req.setImageUrl("http://img/1.jpg");
        when(categoryService.nameOf(anyLong())).thenReturn("手机"); // 不应被调用

        RecognizeSuggestion r = service.recognize(req);
        assertFalse(r.isAvailable(), "无 AI 时 available=false（前端降级手工选择）");
        verify(metrics, times(1)).increment("publish.assist.ai.unavailable");
    }

    @Test
    void recognize_withAi_returnsSuggestion() {
        AiAssistGateway ai = org.mockito.Mockito.mock(AiAssistGateway.class);
        service = new PublishAssistService(categoryService, attrTemplateService, metrics,
                Optional.of(ai), Optional.empty());

        AiAssistGateway.CategorySuggestion s = new AiAssistGateway.CategorySuggestion();
        s.setCategoryId(11L);
        s.setCategoryName("手机");
        s.setConfidence(0.92);
        when(ai.recognizeCategory(anyString(), any())).thenReturn(Optional.of(s));

        RecognizeRequest req = new RecognizeRequest();
        req.setImageUrl("http://img/1.jpg");
        RecognizeSuggestion r = service.recognize(req);
        assertTrue(r.isAvailable());
        assertEquals(11L, r.getCategoryId());
        assertEquals("手机", r.getCategoryName());
        assertEquals(0.92, r.getConfidence());
        verify(metrics, times(1)).increment("publish.assist.ai.used");
    }

    // ---------- REQ-03 属性模板 ----------

    @Test
    void attributes_returnsTemplateList() {
        List<AttrTemplate> list = List.of(new AttrTemplate());
        when(categoryService.getById(anyLong())).thenReturn(new com.idlefish.trade.item.entity.Category());
        when(attrTemplateService.listByCategory(anyLong())).thenReturn(list);

        List<AttrTemplate> result = service.attributes(5L);
        assertEquals(list, result);
    }

    @Test
    void attributes_categoryNotFound_throws() {
        when(categoryService.getById(anyLong())).thenThrow(new BizException(Code.CATEGORY_NOT_FOUND));
        BizException ex = assertThrows(BizException.class, () -> service.attributes(999L));
        assertEquals(Code.CATEGORY_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- REQ-04 估价 ----------

    @Test
    void price_noAi_noBase_hasNoData() {
        PriceSuggestion p = service.price(5L, 1);
        assertFalse(p.isHasData(), "AI 与基准价均缺失 → hasData=false（前端隐藏区间）");
        verify(metrics, times(1)).increment("publish.assist.price.nodata");
    }

    @Test
    void price_ruleFallback_fromReferenceBase() {
        ReferencePriceProvider ref = org.mockito.Mockito.mock(ReferencePriceProvider.class);
        service = new PublishAssistService(categoryService, attrTemplateService, metrics,
                Optional.empty(), Optional.of(ref));
        when(ref.basePriceFen(anyLong(), anyInt())).thenReturn(Optional.of(100_000L));

        PriceSuggestion p = service.price(5L, 1); // level=1 全新 ×1.0 → [90000,110000]
        assertTrue(p.isHasData());
        assertEquals("rule", p.getSource());
        assertEquals(90_000L, p.getMinFen());
        assertEquals(110_000L, p.getMaxFen());
        verify(metrics, times(1)).increment("publish.assist.price.rule");
    }

    @Test
    void price_aiWins_overRule() {
        AiAssistGateway ai = org.mockito.Mockito.mock(AiAssistGateway.class);
        service = new PublishAssistService(categoryService, attrTemplateService, metrics,
                Optional.of(ai), Optional.empty());
        AiAssistGateway.PriceAiSuggestion s = new AiAssistGateway.PriceAiSuggestion();
        s.setMinFen(50_000L);
        s.setMaxFen(60_000L);
        when(ai.suggestPrice(anyLong(), anyInt())).thenReturn(Optional.of(s));

        PriceSuggestion p = service.price(5L, 3);
        assertTrue(p.isHasData());
        assertEquals("ai", p.getSource());
        assertEquals(50_000L, p.getMinFen());
        assertEquals(60_000L, p.getMaxFen());
        verify(metrics, times(1)).increment("publish.assist.price.ai");
    }

    // ---------- REQ-02 草稿 ----------

    @Test
    void draft_ruleFallback_templateAlwaysAvailable() {
        when(categoryService.nameOf(anyLong())).thenReturn("手机");
        DraftRequest req = new DraftRequest();
        req.setCategoryId(5L);
        req.setConditionLevel(1);
        req.setTitle("我的旧手机");

        DraftSuggestion d = service.draft(req);
        assertEquals("rule", d.getSource());
        assertFalse(d.isAiAvailable());
        assertTrue(d.getTitle().contains("手机"));
        assertTrue(d.getDescription().contains("品类：手机"));
        verify(metrics, times(1)).increment("publish.assist.draft.rule");
    }

    @Test
    void draft_aiWins_overRule() {
        AiAssistGateway ai = org.mockito.Mockito.mock(AiAssistGateway.class);
        service = new PublishAssistService(categoryService, attrTemplateService, metrics,
                Optional.of(ai), Optional.empty());
        AiAssistGateway.DraftAiSuggestion s = new AiAssistGateway.DraftAiSuggestion();
        s.setTitle("AI 标题");
        s.setDescription("AI 描述");
        when(ai.generateDraft(anyLong(), anyInt(), any())).thenReturn(Optional.of(s));

        DraftRequest req = new DraftRequest();
        req.setCategoryId(5L);
        req.setConditionLevel(3);
        DraftSuggestion d = service.draft(req);
        assertEquals("ai", d.getSource());
        assertTrue(d.isAiAvailable());
        assertEquals("AI 标题", d.getTitle());
        assertEquals("AI 描述", d.getDescription());
        verify(metrics, times(1)).increment("publish.assist.draft.ai");
    }
}
