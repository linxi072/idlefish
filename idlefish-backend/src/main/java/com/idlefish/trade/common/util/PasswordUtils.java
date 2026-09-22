package com.idlefish.trade.common.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希工具（R-20 安全治理）：基于 JDK 内置 PBKDF2WithHmacSHA256 加盐哈希，
 * 不依赖任何外部安全库。存储格式： ALGO:ITER:SALT:BASE64HASH 。
 * 校验采用定长时间比较，规避计时侧信道；并对遗留明文做一次性兼容（命中即视为旧数据）。
 */
public final class PasswordUtils {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITER = 10000;
    private static final int SALT_LEN = 16;
    private static final int HASH_LEN = 32;
    private static final String PREFIX = "PBKDF2HMACSHA256:" + ITER + ":";

    private PasswordUtils() {}

    /** 对明文密码生成可存储的哈希串。 */
    public static String hash(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw password is null");
        }
        try {
            SecureRandom r = new SecureRandom();
            byte[] salt = new byte[SALT_LEN];
            r.nextBytes(salt);
            byte[] hash = pbkdf2(raw, salt, ITER, HASH_LEN);
            return PREFIX + Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("password hash failed", e);
        }
    }

    /** 校验明文与存储哈希是否匹配。 */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        // 遗留明文兼容：未采用本工具格式的历史数据（仅演示迁移期使用）
        if (!stored.startsWith("PBKDF2HMACSHA256:")) {
            return stored.equals(raw);
        }
        String[] p = stored.split(":", 4);
        if (p.length != 4) {
            return false;
        }
        try {
            int iter = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = pbkdf2(raw, salt, iter, expected.length);
            return slowEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String raw, byte[] salt, int iter, int len) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance(ALGO);
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iter, len * 8);
        return f.generateSecret(spec).getEncoded();
    }

    /** 定长时间比较，规避计时攻击。 */
    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
