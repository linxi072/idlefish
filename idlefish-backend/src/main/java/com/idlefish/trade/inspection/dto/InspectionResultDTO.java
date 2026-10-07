package com.idlefish.trade.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 鉴定机构结果回传入参（REQ-03）：机构回传置「已验通过 / 已验不通过」+ 不可篡改报告快照。
 * 由机构回调接口提交，服务端据此推进验货单状态并落库报告。
 */
@Data
public class InspectionResultDTO {

    @NotBlank(message = "验货单号不能为空")
    private String inspectionNo;

    /** 评级：A/B/C/D。 */
    private String grade;

    /** 是否通过（REQ-03：通过 / 不通过）。 */
    @NotNull(message = "验货结论不能为空")
    private Boolean pass;

    /** 功能项检测结果（JSON 数组）。 */
    private String funcItems;

    /** 瑕疵项（JSON 数组）。 */
    private String flaws;

    /** 报告封面/图（逗号分隔 URL）。 */
    private String coverImages;

    /** 机构原始回传报文（不可篡改存证，TEXT）。 */
    private String rawJson;

    /** 报告版本，默认 1.0。 */
    private String reportVersion;
}
