package com.idlefish.trade.trade.service;

/**
 * 资金托管抽象（PRD §4.3 支付分账托管）。
 * Mock 实现用于本地跑通链路；生产替换为微信支付分账托管实现。
 */
public interface FundEscrowService {

    /** 预下单，返回渠道侧支付参数。 */
    PrepayResult prepay(String payNo, Long amount);

    /** 查询支付是否成功。 */
    boolean queryPaid(String payNo);

    /** 发起退款，返回渠道退款单号。 */
    String refund(String payNo, Long amount);

    /**
     * 分账：将交易款项分给接收方（如平台佣金/卖家），返回渠道分账单号。
     * Mock 直接返回模拟单号；真实实现调用微信支付分账 API。
     */
    String profitShare(String payNo, Long amount, String receiverMchId, Long shareAmount);

    /**
     * 校验支付异步通知的合法性（防伪造支付成功）。
     * Mock 实现直接返回 true（演示信任）；真实实现需基于微信支付平台证书 / APIv3 密钥验签。
     * 该接口不依赖用户令牌，供公开回调端点 /api/pay/notify 内部调用。
     */
    boolean verifyNotify(java.util.Map<String, String> params);

    /**
     * 解密微信支付 v3 回调密文（AES-256-GCM，密钥为 APIv3Key）。
     * 返回解密后的资源明文 Map；解密失败返回 null。Mock 实现返回 null。
     */
    java.util.Map<String, Object> decryptNotify(String resourceJson);

    /**
     * 校验微信支付 v3 回调节点签名（SHA256-RSA，需平台证书公钥）。
     * 未配置平台证书时返回 false（安全失败）。Mock 返回 true。
     */
    boolean verifySignature(String timestamp, String nonce, String body, String signature);
}
