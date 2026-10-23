package com.idlefish.trade.export;

import com.idlefish.trade.common.util.CryptoUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV 构造工具（ITER-T06）：纯函数，无副作用，便于离线单测。
 * <ul>
 *   <li>字段级转义：含逗号/引号/换行的内容包裹双引号并转义内部引号；</li>
 *   <li>手机号脱敏：借助 {@link CryptoUtil#maskPhone}（138****8000）；</li>
 *   <li>统一以 CRLF 换行，首行为表头。</li>
 * </ul>
 */
public final class ExportCsvBuilder {

    private ExportCsvBuilder() {
    }

    /** 由表头 + 数据行构造 CSV 文本（首行表头，随后每行一条记录）。 */
    public static String build(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinRow(headers)).append("\r\n");
        for (List<String> row : rows) {
            sb.append(joinRow(row)).append("\r\n");
        }
        return sb.toString();
    }

    /** 单行拼接（逗号分隔 + 逐字段转义）。 */
    public static String joinRow(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return "";
        }
        return cells.stream().map(ExportCsvBuilder::escape).collect(Collectors.joining(","));
    }

    /** CSV 字段转义：null → 空串；含特殊字符则包裹双引号并翻倍内部引号。 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** 手机号脱敏（调用既有 CryptoUtil.maskPhone，保持全系统脱敏口径一致）。 */
    public static String maskPhone(String phone) {
        return CryptoUtil.maskPhone(phone);
    }
}
