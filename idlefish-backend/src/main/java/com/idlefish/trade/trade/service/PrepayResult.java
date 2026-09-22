package com.idlefish.trade.trade.service;

import lombok.Data;

/**
 * 预下单结果。
 */
@Data
public class PrepayResult {

    private String prepayId;
    private String channel;
    private String extra; // 渠道附加参数；Mock 下为模拟支付提示
}
