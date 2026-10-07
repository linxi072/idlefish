package com.idlefish.trade.publish.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * REQ-02 草稿生成请求。
 */
@Data
public class DraftRequest {
    @NotNull
    private Long categoryId;
    private Integer conditionLevel;
    private String title;
}
