package com.idlefish.trade.publish.vo;

import lombok.Data;

/**
 * REQ-01 图片识别建议类目。
 */
@Data
public class RecognizeSuggestion {
    /** AI 是否可用（不可用时 available=false，前端降级为手工选择类目，不阻塞发布）。 */
    private boolean available;
    private Long categoryId;
    private String categoryName;
    /** 识别置信度，低则前端不预填类目（边界1）。 */
    private Double confidence;
}
