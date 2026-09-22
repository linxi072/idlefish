package com.idlefish.trade.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 地址新增/编辑入参。 */
@Data
public class AddressDTO {
    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 是否设为默认 0/1 */
    private Integer isDefault;
}
