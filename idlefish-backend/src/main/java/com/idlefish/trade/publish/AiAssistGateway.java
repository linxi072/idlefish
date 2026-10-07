package com.idlefish.trade.publish;

import lombok.Data;

import java.util.Optional;

/**
 * AI 发布辅助网关（U02 扩展点，依赖 ITER-F03 AI 智能定价）。
 * 默认方法均返回 empty，实现「AI 不可用 → 降级为空 / 规则兜底」（REQ-05 断网/AI 超时不阻塞发布）。
 *
 * <p>接入方式：AI 团队实现本接口并注册为 Spring Bean（建议标记 {@code @Primary}）后，
 * 发布辅助将优先采用 AI 结果（类目识别 / 估价 / 草稿），无需改动本模块其它代码。
 */
public interface AiAssistGateway {

    /** 图片识别 → 建议类目（REQ-01）。无 AI 时返回 empty。 */
    default Optional<CategorySuggestion> recognizeCategory(String imageUrl, String imageMeta) {
        return Optional.empty();
    }

    /** AI 估价（REQ-04）。无 AI 时返回 empty，由规则基准价兜底或显式无数据。 */
    default Optional<PriceAiSuggestion> suggestPrice(Long categoryId, Integer conditionLevel) {
        return Optional.empty();
    }

    /** AI 生成标题/描述草稿（REQ-02）。无 AI 时返回 empty，由规则模板兜底。 */
    default Optional<DraftAiSuggestion> generateDraft(Long categoryId, Integer conditionLevel, String title) {
        return Optional.empty();
    }

    /** 类目识别建议（AI 返回）。confidence 低时前端不预填类目（边界1）。 */
    @Data
    class CategorySuggestion {
        private Long categoryId;
        private String categoryName;
        private Double confidence;
    }

    /** AI 估价结果（分）。 */
    @Data
    class PriceAiSuggestion {
        private long minFen;
        private long maxFen;
    }

    /** AI 草稿。 */
    @Data
    class DraftAiSuggestion {
        private String title;
        private String description;
    }
}
