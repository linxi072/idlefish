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
     * 校验支付异步通知的合法性（防伪造支付成功）。
     * Mock 实现直接返回 true（演示信任）；真实实现需基于微信支付平台证书 / APIv3 密钥验签。
     * 该接口不依赖用户令牌，供公开回调端点 /api/pay/notify 内部调用。
     */
    boolean verifyNotify(java.util.Map<String, String> params);
}
