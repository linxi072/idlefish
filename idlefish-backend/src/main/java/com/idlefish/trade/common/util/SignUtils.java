package com.idlefish.trade.common.util;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

/**
 * 签名 / 摘要工具（零外部依赖，仅用 JDK 标准库）。
 * 服务于：阿里云 OSS（HMAC-SHA1）、阿里云内容安全（HMAC-SHA1）、
 * 微信支付 v3（RSA-SHA256）、RocketMQ（HMAC-SHA1）等真实对接的实现。
 */
public final class SignUtils {

    private SignUtils() {
    }

    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String s) {
        return Base64.getDecoder().decode(s);
    }

    /** SHA-256 十六进制。 */
    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("sha256 计算失败", e);
        }
    }

    /** MD5 十六进制（阿里云 OSS 表单/部分场景使用）。 */
    public static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("md5 计算失败", e);
        }
    }

    /** MD5 原始字节（绿网 Content-MD5 等）。 */
    public static byte[] md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("md5 计算失败", e);
        }
    }

    /** HMAC-SHA256（Base64 输出，微信支付/AES 密钥派生等）。 */
    public static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac-sha256 计算失败", e);
        }
    }

    /** HMAC-SHA1（Base64 输出，阿里云 OSS / 内容安全 / RocketMQ 使用）。 */
    public static String hmacSha1(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return base64(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac-sha1 计算失败", e);
        }
    }

    /** RSA-SHA256 签名（微信支付 v3 Authorization），Base64 输出。 */
    public static String rsaSignSha256(String data, PrivateKey key) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return base64(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("rsa-sha256 签名失败", e);
        }
    }

    /** 从 PEM（PKCS#8）字符串加载 RSA 私钥（微信支付商户 API 私钥）。 */
    public static PrivateKey loadPrivateKeyFromPem(String pem) {
        try {
            String cleaned = pem
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = base64Decode(cleaned);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("加载 RSA 私钥失败", e);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
