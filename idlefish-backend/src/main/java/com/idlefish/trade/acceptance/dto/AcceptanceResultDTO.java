package com.idlefish.trade.acceptance.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验收用例结果回填入参（ITER-F01）。
 * 由真机/商户号侧验收执行后回填 PASSED / FAILED / SKIPPED。
 */
@Data
public class AcceptanceResultDTO {

    /** 验收结论：PASSED / FAILED / SKIPPED（SKIPPED=沙箱不适用，已确认跳过）。 */
    @Pattern(regexp = "PASSED|FAILED|SKIPPED", message = "验收结论须为 PASSED/FAILED/SKIPPED")
    private String result;

    /** 验收执行人（可选）。 */
    @Size(max = 64, message = "执行人长度不可超过 64")
    private String executor;

    /** 结果备注（失败原因、真机环境说明等，可选）。 */
    @Size(max = 512, message = "备注长度不可超过 512")
    private String resultNote;

    /** 验收执行时间（可选，缺省由服务端置为当前时间）。 */
    private LocalDateTime executedAt;
}
