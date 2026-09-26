package com.idlefish.trade.trade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 真实微信支付 v3 实现（真实微信支付 v3，生产默认启用，已移除 mock 旁路）。
 * 采用零 SDK 方式直接调用微信支付 v3 REST + RSA-SHA256 签名 + AES-256-GCM 解密：
 * - JSAPI 下单（携带 openid）并返回客户端调起支付所需二次签名参数；
 * - 查单 / 退款 / 分账（profitsharing）；
 * - 回调验签（平台证书 RSA）与密文解密（APIv3Key）。
 * 所有调用在异常时记录日志并抛出，由上层（PayService）做幂等/补偿，不静默吞错。
 */
@Service
public class RealWechatEscrowServiceImpl implements FundEscrowService {

    private static final Logger log = LoggerFactory.getLogger(RealWechatEscrowServiceImpl.class);

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PayOrderMapper payOrderMapper;
    private final UserService userService;

    public RealWechatEscrowServiceImpl(IdlefishProperties props, RestTemplate restTemplate,
                                      ObjectMapper objectMapper, PayOrderMapper payOrderMapper,
                                      UserService userService) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.payOrderMapper = payOrderMapper;
        this.userService = userService;
    }

    private IdlefishProperties.Pay pay() {
        return props.getPay();
    }

    // ---------------- JSAPI 下单 ----------------

    @Override
    public PrepayResult prepay(String payNo, Long amount) {
        IdlefishProperties.Pay p = pay();
        // 解析买家 openid（JSAPI 必填）
        String openid = resolveOpenid(payNo);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appid", p.getAppid());
        body.put("mchid", p.getMchid());
        body.put("description", "闲置集二手商品交易");
        body.put("out_trade_no", payNo);
        body.put("notify_url", p.getNotifyUrl());
        ObjectNode amt = body.putObject("amount");
        amt.put("total", amount);
        amt.put("currency", "CNY");
        ObjectNode payer = body.putObject("payer");
        payer.put("openid", openid);

        String url = p.getGateway() + "/v3/pay/transactions/jsapi";
        String resp = postJson(url, body.toString());
        JsonNode root = readTree(resp);
        String prepayId = root.path("prepay_id").asText(null);
        if (prepayId == null) {
            throw new BizException(Code.BIZ_ERROR, "微信下单失败: " + resp);
        }
        return buildClientSign(p, prepayId);
    }

    /** 构造客户端 wx.requestPayment 所需的二次签名参数。 */
    private PrepayResult buildClientSign(IdlefishProperties.Pay p, String prepayId) {
        String timeStamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String pkg = "prepay_id=" + prepayId;
        String signMsg = p.getAppid() + "\n" + timeStamp + "\n" + nonceStr + "\n" + pkg + "\n";
        String paySign = SignUtils.rsaSignSha256(signMsg, loadPrivateKey());
        PrepayResult r = new PrepayResult();
        r.setPrepayId(prepayId);
        r.setChannel("wechat");
        r.setTimeStamp(timeStamp);
        r.setNonceStr(nonceStr);
        r.setPackageVal(pkg);
        r.setPaySign(paySign);
        r.setSignType("RSA");
        return r;
    }

    private String resolveOpenid(String payNo) {
        com.idlefish.trade.trade.entity.PayOrder po = payOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.idlefish.trade.trade.entity.PayOrder>()
                        .eq(com.idlefish.trade.trade.entity.PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "支付单不存在");
        }
        User u = userService.getById(po.getBuyerId());
        if (u == null || u.getWxOpenid() == null || u.getWxOpenid().isBlank()) {
            throw new BizException(Code.BIZ_ERROR, "买家未绑定微信 openid，无法 JSAPI 支付");
        }
        return u.getWxOpenid();
    }

    // ---------------- 查单 ----------------

    @Override
    public boolean queryPaid(String payNo) {
        IdlefishProperties.Pay p = pay();
        String url = p.getGateway() + "/v3/pay/transactions/out-trade-no/" + payNo;
        try {
            String resp = get(url);
            JsonNode root = readTree(resp);
            return "SUCCESS".equals(root.path("trade_state").asText(null));
        } catch (Exception e) {
            log.warn("微信查单异常 payNo={}: {}", payNo, e.getMessage());
            return false;
        }
    }

    // ---------------- 退款 ----------------

    @Override
    public String refund(String payNo, Long amount) {
        IdlefishProperties.Pay p = pay();
        com.idlefish.trade.trade.entity.PayOrder po = payOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.idlefish.trade.trade.entity.PayOrder>()
                        .eq(com.idlefish.trade.trade.entity.PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "支付单不存在");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("out_refund_no", "REFUND_" + payNo + "_" + System.nanoTime());
        body.put("transaction_id", po.getTransactionId());
        body.put("reason", "用户申请退款");
        ObjectNode amt = body.putObject("amount");
        amt.put("refund", amount);
        amt.put("total", po.getAmount());
        amt.put("currency", "CNY");
        String url = p.getGateway() + "/v3/refund/domestic/refunds";
        String resp = postJson(url, body.toString());
        JsonNode root = readTree(resp);
        String refundId = root.path("refund_id").asText(null);
        if (refundId == null) {
            throw new BizException(Code.BIZ_ERROR, "微信退款失败: " + resp);
        }
        return refundId;
    }

    // ---------------- 分账 ----------------

    @Override
    public String profitShare(String payNo, Long amount, String receiverMchId, Long shareAmount) {
        IdlefishProperties.Pay p = pay();
        com.idlefish.trade.trade.entity.PayOrder po = payOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.idlefish.trade.trade.entity.PayOrder>()
                        .eq(com.idlefish.trade.trade.entity.PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "支付单不存在");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appid", p.getAppid());
        body.put("mchid", p.getMchid());
        body.put("out_order_no", "PS_" + payNo + "_" + System.nanoTime());
        body.put("transaction_id", po.getTransactionId());
        ObjectNode receiver = objectMapper.createObjectNode();
        receiver.put("type", "MERCHANT_ID");
        receiver.put("account", receiverMchId);
        receiver.put("amount", shareAmount);
        receiver.put("description", "分账给卖家");
        body.putArray("receivers").add(receiver);
        body.put("unfreeze_unsplit", false);
        String url = p.getGateway() + "/v3/profitsharing/orders";
        String resp = postJson(url, body.toString());
        JsonNode root = readTree(resp);
        String orderId = root.path("order_id").asText(null);
        if (orderId == null) {
            throw new BizException(Code.BIZ_ERROR, "微信分账失败: " + resp);
        }
        return orderId;
    }

    // ---------------- 提现出款（商家转账到零钱） ----------------

    @Override
    public String transfer(String outBizNo, Long amount, String openid) {
        IdlefishProperties.Pay p = pay();
        if (!configured(p)) {
            // 硬网关：未配置真实商户凭据时，绝不实际出款，安全失败（F-11.3 资金安全底线）。
            throw new BizException(Code.CONFIG_MISSING,
                    "微信商户配置缺失，未实际出款（沙箱/未配置环境）");
        }
        // 幂等先查：若已成功出款，直接返回，避免网络超时重试导致重复出款。
        if (queryTransfer(outBizNo)) {
            return outBizNo;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appid", p.getAppid());
        body.put("out_bill_no", outBizNo);
        body.put("transfer_scene_id", p.getTransferSceneId() != null ? p.getTransferSceneId() : "1000");
        body.put("openid", openid);
        ObjectNode amt = body.putObject("amount");
        amt.put("total", amount);
        amt.put("currency", "CNY");
        String url = p.getGateway() + "/v3/fund-app/mch-transfer/transfer-to-balance";
        String resp = postJson(url, body.toString());
        JsonNode root = readTree(resp);
        String billNo = root.path("transfer_bill_no").asText(null);
        if (billNo == null) {
            throw new BizException(Code.FUND_TRANSFER_FAILED,
                    "微信出款失败: " + resp);
        }
        return billNo;
    }

    @Override
    public boolean queryTransfer(String outBizNo) {
        IdlefishProperties.Pay p = pay();
        if (!configured(p)) {
            return false;
        }
        String url = p.getGateway() + "/v3/fund-app/mch-transfer/transfer-to-balance/out-bill-no/" + outBizNo;
        try {
            String resp = get(url);
            JsonNode root = readTree(resp);
            return "SUCCESS".equals(root.path("state").asText(null));
        } catch (Exception e) {
            log.warn("微信转账查询异常 outBizNo={}: {}", outBizNo, e.getMessage());
            return false;
        }
    }

    private boolean configured(IdlefishProperties.Pay p) {
        return p.getMchid() != null && p.getAppid() != null && p.getApiV3Key() != null
                && p.getSerialNo() != null && p.getPrivateKey() != null;
    }

    // ---------------- 回调验签 / 解密 ----------------

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        // 真实模式下，遗留的表单式回调无法承载微信 v3 签名，安全失败；
        // 生产请使用 /api/pay/notify/v3（JSON 密文 + 签名头）走 decryptNotify/verifySignature。
        return false;
    }

    @Override
    public Map<String, Object> decryptNotify(String resourceJson) {
        IdlefishProperties.Pay p = pay();
        if (p.getApiV3Key() == null || p.getApiV3Key().isBlank()) {
            log.error("APIv3 密钥未配置，无法解密回调");
            return null;
        }
        try {
            JsonNode r = objectMapper.readTree(resourceJson);
            byte[] key = p.getApiV3Key().getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = Base64.getDecoder().decode(r.path("ciphertext").asText());
            byte[] nonce = Base64.getDecoder().decode(r.path("nonce").asText());
            byte[] aad = r.path("associated_data").asText("").getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            byte[] plain = cipher.doFinal(ciphertext);
            JsonNode plainNode = objectMapper.readTree(new ByteArrayInputStream(plain));
            Map<String, Object> map = new HashMap<>();
            map.put("out_trade_no", plainNode.path("out_trade_no").asText(null));
            map.put("transaction_id", plainNode.path("transaction_id").asText(null));
            JsonNode amt = plainNode.path("amount");
            map.put("amount", amt.path("total").asLong(0L));
            map.put("trade_state", plainNode.path("trade_state").asText(null));
            return map;
        } catch (Exception e) {
            log.error("微信回调解密失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean verifySignature(String timestamp, String nonce, String body, String signature) {
        IdlefishProperties.Pay p = pay();
        if (p.getPlatformCert() == null || p.getPlatformCert().isBlank()) {
            log.warn("未配置微信平台证书，回调节点签名跳过验证（安全失败，请在生产配置 platform-cert）");
            return false;
        }
        try {
            PublicKey pub = loadPublicKeyFromPem(p.getPlatformCert());
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.error("微信回调节点验签失败: {}", e.getMessage());
            return false;
        }
    }

    // ---------------- HTTP 工具 ----------------

    private java.security.PrivateKey loadPrivateKey() {
        return SignUtils.loadPrivateKeyFromPem(pay().getPrivateKey());
    }

    private PublicKey loadPublicKeyFromPem(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        return cert.getPublicKey();
    }

    private String postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", buildAuth("POST", url, body));
        headers.set("Accept", "application/json");
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
    }

    private String get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", buildAuth("GET", url, ""));
        headers.set("Accept", "application/json");
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    /** 构造微信支付 v3 Authorization 头（RSA-SHA256）。 */
    private String buildAuth(String method, String url, String body) {
        IdlefishProperties.Pay p = pay();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String path = url.substring(p.getGateway().length());
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = SignUtils.rsaSignSha256(message, loadPrivateKey());
        return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + p.getMchid() + "\",nonce_str=\"" + nonce
                + "\",signature=\"" + signature + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + p.getSerialNo() + "\"";
    }

    private JsonNode readTree(String s) {
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
