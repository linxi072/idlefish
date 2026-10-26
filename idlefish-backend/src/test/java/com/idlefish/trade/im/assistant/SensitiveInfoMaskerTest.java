package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感信息脱敏纯函数单测（ITER-F06 / REQ-13）。
 */
class SensitiveInfoMaskerTest {

    @Test
    void masksPhoneKeepingHeadAndTail() {
        assertEquals("联系138****8000", SensitiveInfoMasker.mask("联系13812348000"));
        assertTrue(SensitiveInfoMasker.hasPhone("电话13812348000"));
        assertFalse(SensitiveInfoMasker.hasPhone("电话1381234800"));
    }

    @Test
    void masksQqAndWechat() {
        assertEquals("QQ：****", SensitiveInfoMasker.mask("QQ：123456"));
        assertEquals("微信号****", SensitiveInfoMasker.mask("微信号abc12345xyz"));
        assertTrue(SensitiveInfoMasker.hasSensitive("加我微信 happy2024"));
    }

    @Test
    void masksCaptcha() {
        assertEquals("验证码是****", SensitiveInfoMasker.mask("验证码是123456"));
    }

    @Test
    void masksAddressByKeywordAndDetail() {
        assertTrue(SensitiveInfoMasker.mask("地址：上海市杨浦区XX路12号").contains("****"));
        assertTrue(SensitiveInfoMasker.hasAddress("寄到上海市杨浦区XX路12号"));
        assertFalse(SensitiveInfoMasker.hasAddress("今天天气不错"));
    }

    @Test
    void plainTextUnchanged() {
        String s = "什么时候发货，支持顺丰吗";
        assertEquals(s, SensitiveInfoMasker.mask(s));
        assertFalse(SensitiveInfoMasker.hasSensitive(s));
    }

    @Test
    void nullAndEmptySafe() {
        assertEquals(null, SensitiveInfoMasker.mask(null));
        assertEquals("", SensitiveInfoMasker.mask(""));
        assertFalse(SensitiveInfoMasker.hasSensitive(null));
    }
}
