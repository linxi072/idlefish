package com.idlefish.trade.export;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExportType 枚举解析单测（ITER-T06）：code 解析、大小写不敏感、非法值拒绝。
 */
class ExportTypeTest {

    @Test
    void fromCode_exact() {
        assertSame(ExportType.ADMIN_USER, ExportType.fromCode("admin_user"));
        assertSame(ExportType.ITEM, ExportType.fromCode("item"));
        assertSame(ExportType.USER, ExportType.fromCode("user"));
        assertSame(ExportType.ORDER, ExportType.fromCode("order"));
    }

    @Test
    void fromCode_caseInsensitive() {
        assertSame(ExportType.ITEM, ExportType.fromCode("ITEM"));
        assertSame(ExportType.USER, ExportType.fromCode("User"));
    }

    @Test
    void fromCode_nullThrows() {
        BizException ex = assertThrows(BizException.class, () -> ExportType.fromCode(null));
        assertEquals(Code.EXPORT_TYPE_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void fromCode_unknownThrows() {
        BizException ex = assertThrows(BizException.class, () -> ExportType.fromCode("nope"));
        assertEquals(Code.EXPORT_TYPE_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void hasOrgScopeSemantics() {
        assertTrue(ExportType.ADMIN_USER.isHasOrgScope());
        assertTrue(ExportType.ITEM.isHasOrgScope());
        assertFalse(ExportType.USER.isHasOrgScope());
        assertFalse(ExportType.ORDER.isHasOrgScope());
    }
}
