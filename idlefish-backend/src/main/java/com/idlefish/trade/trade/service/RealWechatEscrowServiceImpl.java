package com.idlefish.trade.trade.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 真实微信支付分账托管实现骨架（idlefish.pay.mock=false 时启用）。
 * 对接 wx.requestPayment / 分账 API / 退款 API，此处仅占位。
 */
@Service
@ConditionalOnProperty(name = "idlefish.pay.mock", havingValue = "false")
public class RealWechatEscrowServiceImpl implements FundEscrowService {

    @Override
    public PrepayResult prepay(String payNo, Long amount) {
        throw new UnsupportedOperationException("待实现：调用微信支付 JSAPI 下单 / 分账API");
    }

    @Override
    public boolean queryPaid(String payNo) {
        throw new UnsupportedOperationException("待实现：调用微信支付查单接口");
    }

    @Override
    public String refund(String payNo, Long amount) {
        throw new UnsupportedOperationException("待实现：调用微信支付退款接口");
    }

    @Override
    public boolean verifyNotify(java.util.Map<String, String> params) {
        // 生产接入点：使用微信支付平台证书 + APIv3 密钥验签（微信回调为 AES-256-GCM 密文 + 签名）。
        // 在未配置证书/密钥前，显式失败（安全失败）以避免伪造支付通知被信任。
        throw new UnsupportedOperationException("待实现：微信支付回调验签（配置平台证书与 APIv3 密钥后启用）");
    }
}
