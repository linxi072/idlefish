package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.enhance.service.SearchEnhanceService;
import com.idlefish.trade.search.service.RecommendService;
import com.idlefish.trade.search.serving.dto.ItemQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内联模式适配器（ITER-T04 / REQ-10 默认模式）：委托既有进程内搜推能力，
 * 零新增外部依赖，保证离线 {@code mvn -o} 构建与既有行为不变。
 * <p>
 * 适配边界：把 {@link SearchRecommendGateway} 端口映射到
 * {@link SearchEnhanceService}（增强检索/兜底/行为）与 {@link RecommendService}（推荐流），
 * 失败统一包装为 {@link SearchServingException} 交由上层降级。
 */
@Component
public class InlineSearchRecommendGateway implements SearchRecommendGateway {

    private final SearchEnhanceService searchEnhanceService;
    private final RecommendService recommendService;

    public InlineSearchRecommendGateway(SearchEnhanceService searchEnhanceService,
                                        RecommendService recommendService) {
        this.searchEnhanceService = searchEnhanceService;
        this.recommendService = recommendService;
    }

    @Override
    public String mode() {
        return "inline";
    }

    @Override
    public IPage<ItemVO> search(ItemQuery q, Long userId) {
        try {
            return searchEnhanceService.enhancedSearch(q.toQueryDTO(), userId);
        } catch (RuntimeException e) {
            throw new SearchServingException("inline_error", "内联增强检索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ItemVO> guess(String keyword, int limit) {
        try {
            return searchEnhanceService.guessYouWant(keyword, limit);
        } catch (RuntimeException e) {
            throw new SearchServingException("inline_error", "内联兜底召回失败: " + e.getMessage(), e);
        }
    }

    @Override
    public IPage<ItemVO> feed(String city, Long userId, int page, int size) {
        try {
            return recommendService.feed(city, userId, page, size);
        } catch (RuntimeException e) {
            throw new SearchServingException("inline_error", "内联推荐流失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void recordBehavior(Long userId, String event, String bizId) {
        try {
            searchEnhanceService.recordBehavior(userId, event, bizId);
        } catch (RuntimeException e) {
            throw new SearchServingException("inline_error", "内联行为回传失败: " + e.getMessage(), e);
        }
    }
}
