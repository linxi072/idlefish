package com.idlefish.trade.acceptance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 真机验收缺陷登记入参（ITER-F01 / REQ-09 缺陷回链）。
 */
@Data
public class AcceptanceDefectCreateDTO {

    /** 关联验收用例 ID（可选：部分缺陷不绑定具体用例，仅回链 PRD）。 */
    private Long caseId;

    /** 关联需求编号：REQ-01 ~ REQ-09。 */
    @NotBlank(message = "关联需求编号不可为空")
    @Size(max = 16, message = "需求编号长度不可超过 16")
    private String reqCode;

    /** 缺陷标题。 */
    @NotBlank(message = "缺陷标题不可为空")
    @Size(max = 128, message = "缺陷标题长度不可超过 128")
    private String title;

    /** 严重级别：P0 / P1 / P2。 */
    @NotBlank(message = "严重级别不可为空")
    @Pattern(regexp = "P0|P1|P2", message = "严重级别须为 P0/P1/P2")
    private String severity;

    /** 回链 PRD 文档标识（缺省由服务端置为 PRD-P0-F01-真机验收闭环）。 */
    @Size(max = 255, message = "回链 PRD 长度不可超过 255")
    private String linkedPrd;

    /** 缺陷描述（可选）。 */
    @Size(max = 1024, message = "缺陷描述长度不可超过 1024")
    private String description;
}
