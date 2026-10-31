package com.idlefish.trade.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 授权项操作入参（授予 / 撤回）。 */
@Data
public class ConsentDTO {

    /** 授权项编码，见 ConsentItem（phone/address/real_name/location/marketing_push）。 */
    @NotBlank(message = "授权项不能为空")
    private String consentCode;
}
