package com.idlefish.trade.compliance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 个人信息脱敏纯函数单测（ITER-U05 REQ-11）。 */
class PersonalInfoMaskerTest {

    @Test
    void maskPhone_keeps_prefix_and_suffix() {
        assertEquals("138****8000", PersonalInfoMasker.maskPhone("13812348000"));
        assertEquals("****", PersonalInfoMasker.maskPhone("123"));
        assertEquals("", PersonalInfoMasker.maskPhone(null));
        assertEquals("", PersonalInfoMasker.maskPhone("   "));
    }

    @Test
    void maskIdCard_and_bankCard() {
        assertEquals("110101********1234",
                PersonalInfoMasker.maskIdCard("110101199001011234"));
        assertEquals("**** 1234", PersonalInfoMasker.maskBankCard("6222 0200 1234 1234"));
    }

    @Test
    void maskAddress_nickname_email() {
        assertEquals("广东省深圳市****(详细地址已隐藏)",
                PersonalInfoMasker.maskAddress("广东省深圳市南山区科技园1栋101"));
        assertEquals("张****", PersonalInfoMasker.maskNickname("张三丰"));
        assertEquals("z****@qq.com", PersonalInfoMasker.maskEmail("zhangsan@qq.com"));
        assertEquals("****", PersonalInfoMasker.maskEmail("no-at-sign"));
    }

    @Test
    void masked_value_never_contains_full_sensitive_text() {
        String phone = "13812348000";
        String masked = PersonalInfoMasker.maskPhone(phone);
        assertFalse(masked.equals(phone));
        assertFalse(masked.contains("1234"), "中段四位不得出现在脱敏结果中");
    }
}
