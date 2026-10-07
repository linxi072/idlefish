package com.idlefish.trade.publish;

import java.util.Optional;

/**
 * 历史成交基准价提供方（U01 规则兜底数据源，REQ-04）。
 * 默认 empty，表示「无历史成交数据，不展示估价区间」（边界2 / REQ-05 降级）。
 *
 * <p>接入方式：实现本接口并注册为 Spring Bean 后，发布辅助即可基于历史成交给出规则估价区间；
 * 与 {@link AiAssistGateway} 并存时，AI 估价优先，规则兜底次之。
 */
public interface ReferencePriceProvider {

    /**
     * 某类目某成色的基准价（分）。无数据返回 empty。
     */
    Optional<Long> basePriceFen(Long categoryId, Integer conditionLevel);
}
