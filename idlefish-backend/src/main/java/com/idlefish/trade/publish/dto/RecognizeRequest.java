package com.idlefish.trade.publish.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * REQ-01 图片识别请求。
 */
@Data
public class RecognizeRequest {
    @NotBlank
    private String imageUrl;
    private String imageMeta;
}
