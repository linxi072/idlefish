package com.idlefish.trade.common.util;

import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 对称加密工具：用于手机号等敏感字段的加密存储（本地 Mock 实现）。
 * 生产环境应使用 KMS / 配置中心托管密钥，并采用随机 IV 的 GCM 模式。
 */
@Component
public class CryptoUtil {

    private static final String ALGO = "AES/CBC/PKCS5Padding";
    private static final byte[] FIXED_IV = "0000000000000000".getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec keySpec;

    public CryptoUtil(IdlefishProperties props) {
        byte[] bytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 16) {
            byte[] padded = new byte[16];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        byte[] keyBytes = new byte[16];
        System.arraycopy(bytes, 0, keyBytes, 0, 16);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(FIXED_IV));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.SYSTEM_ERROR, "加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(FIXED_IV));
            byte[] dec = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.SYSTEM_ERROR, "解密失败");
        }
    }

    /** 手机号脱敏：138****8000 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
