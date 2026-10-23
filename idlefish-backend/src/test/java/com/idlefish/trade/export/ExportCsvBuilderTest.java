package com.idlefish.trade.export;

import com.idlefish.trade.common.util.CryptoUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExportCsvBuilder 纯函数单测（ITER-T06）：转义、行拼接、脱敏、整体构造。
 * 离线可跑，无 Spring / MySQL 依赖。
 */
class ExportCsvBuilderTest {

    @Test
    void escape_plain() {
        assertEquals("hello", ExportCsvBuilder.escape("hello"));
    }

    @Test
    void escape_nullToEmpty() {
        assertEquals("", ExportCsvBuilder.escape(null));
    }

    @Test
    void escape_quotesAndCommasWrapped() {
        // 含逗号 → 包裹并转义内部引号
        assertEquals("\"a,b\"", ExportCsvBuilder.escape("a,b"));
        assertEquals("\"he said \"\"hi\"\"\"", ExportCsvBuilder.escape("he said \"hi\""));
    }

    @Test
    void escape_newlineWrapped() {
        assertEquals("\"line1\nline2\"", ExportCsvBuilder.escape("line1\nline2"));
    }

    @Test
    void joinRow_joinWithComma() {
        assertEquals("a,b,c", ExportCsvBuilder.joinRow(List.of("a", "b", "c")));
    }

    @Test
    void joinRow_emptyOrNull() {
        assertEquals("", ExportCsvBuilder.joinRow(List.of()));
        assertEquals("", ExportCsvBuilder.joinRow(null));
    }

    @Test
    void build_headerThenRowsWithCrlf() {
        String csv = ExportCsvBuilder.build(
                List.of("id", "name"),
                List.of(List.of("1", "alice"), List.of("2", "bob")));
        String[] lines = csv.split("\r\n");
        assertEquals(3, lines.length); // 头 + 2 行 + 末尾换行产生的空串
        assertEquals("id,name", lines[0]);
        assertEquals("1,alice", lines[1]);
        assertEquals("2,bob", lines[2]);
    }

    @Test
    void build_specialCharsEscapedInRows() {
        String csv = ExportCsvBuilder.build(
                List.of("note"),
                List.of(List.of("a,b")));
        assertTrue(csv.contains("\"a,b\""));
    }

    @Test
    void maskPhone_delegatesToCryptoUtil() {
        String phone = "13800001111";
        assertEquals(CryptoUtil.maskPhone(phone), ExportCsvBuilder.maskPhone(phone));
        assertNotNull(ExportCsvBuilder.maskPhone(phone));
        assertTrue(ExportCsvBuilder.maskPhone(phone).contains("****"));
    }
}
