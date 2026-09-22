package com.idlefish.trade.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 地址对外视图：手机号脱敏展示。 */
@Data
public class AddressVO {
    private Long id;
    private String receiverName;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Integer isDefault;
    private LocalDateTime createdAt;
}
