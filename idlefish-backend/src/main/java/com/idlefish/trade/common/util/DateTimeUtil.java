package com.idlefish.trade.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间格式化工具：集中维护全局统一的格式常量，避免各服务重复内联
 * {@code DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")}。
 * 既有服务保留 {@code private static final DateTimeFormatter FMT = DateTimeUtil.FMT;} 别名以兼容已有调用点。
 */
public final class DateTimeUtil {

    /** 全局统一日期时间格式（分→秒，与数据库 DATETIME、前端 value-format 对齐）。 */
    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 全局统一格式器实例（不可变、线程安全）。 */
    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern(PATTERN);

    private DateTimeUtil() {
    }

    /** 格式化为 {@link #PATTERN} 字符串；null 返回空串。 */
    public static String format(LocalDateTime t) {
        return t == null ? "" : FMT.format(t);
    }

    /** 仅取日期部分（yyyy-MM-dd）；null 返回空串。 */
    public static String formatDate(LocalDateTime t) {
        return t == null ? "" : t.toLocalDate().toString();
    }
}
