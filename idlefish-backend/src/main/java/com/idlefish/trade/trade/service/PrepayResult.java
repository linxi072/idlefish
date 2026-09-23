package com.idlefish.trade.trade.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 预下单结果。
 * 真实微信支付（JSAPI）模式下额外携带客户端调起支付所需的签名参数（timeStamp/nonceStr/package/paySign/signType）。
 */
@Data
public class PrepayResult {

    private String prepayId;
    private String channel;
    private String extra; // 渠道附加参数；Mock 下为模拟支付提示

    // ===== 微信 JSAPI 客户端调起支付参数（wx.requestPayment）=====
    private String timeStamp;
    private String nonceStr;
    @JsonProperty("package")
    private String packageVal;
    private String paySign;
    private String signType = "RSA";
}
