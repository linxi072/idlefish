package com.idlefish.trade.trade.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 本地 Mock 资金托管：直接信任支付结果，便于跑通交易闭环。
 */
@Service
@Primary
@ConditionalOnProperty(name = "idlefish.pay.mock", havingValue = "true", matchIfMissing = true)
public class MockWechatEscrowServiceImpl implements FundEscrowService {

    @Override
    public PrepayResult prepay(String payNo, Long amount) {
        PrepayResult r = new PrepayResult();
        r.setPrepayId("MOCK_PREPAY_" + payNo);
        r.setChannel("wechat_mock");
        r.setExtra("模拟微信支付预下单，请调用 /api/pay/mock/{payNo} 完成支付");
        return r;
    }

    @Override
    public boolean queryPaid(String payNo) {
        // Mock 环境信任 notify 回调
        return true;
    }

    @Override
    public String refund(String payNo, Long amount) {
        return "MOCK_REFUND_" + payNo + "_" + System.nanoTime();
    }

    @Override
    public String profitShare(String payNo, Long amount, String receiverMchId, Long shareAmount) {
        return "MOCK_PROFIT_" + payNo + "_" + System.nanoTime();
    }

    @Override
    public boolean verifyNotify(java.util.Map<String, String> params) {
        // 演示环境直接信任回调；生产必须将 idlefish.pay.mock=false 并接入真实验签
        return true;
    }

    @Override
    public java.util.Map<String, Object> decryptNotify(String resourceJson) {
        return null;
    }

    @Override
    public boolean verifySignature(String timestamp, String nonce, String body, String signature) {
        return true;
    }
}
