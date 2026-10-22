package com.idlefish.trade.datascope;

/**
 * 数据范围（REQ-02）：四种范围，按角色配置。
 * <ul>
 *   <li>{@code SELF}          —— 仅本机构</li>
 *   <li>{@code SELF_AND_CHILD}—— 本机构及全部下级</li>
 *   <li>{@code APPOINT}       —— 指定机构集合</li>
 *   <li>{@code ALL}           —— 全部（上下文 orgIds=null 表示不受限）</li>
 * </ul>
 * 默认从严（REQ-09）：未声明范围时按「仅本机构」处理。
 */
public enum DataScope {

    SELF,
    SELF_AND_CHILD,
    APPOINT,
    ALL;

    /** 默认范围：仅本机构（REQ-09 从严）。 */
    public static DataScope defaultScope() {
        return SELF;
    }

    /** 由字符串解析；非法/空值回落到默认范围（REQ-09）。 */
    public static DataScope fromCode(String code) {
        if (code == null || code.isBlank()) {
            return defaultScope();
        }
        try {
            return DataScope.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultScope();
        }
    }

    /** 范围码（用于持久化 / 配置）。 */
    public String code() {
        return name();
    }

    /** 范围权重：用于多角色合并时取「最宽」（ALL > SELF_AND_CHILD > APPOINT > SELF）。 */
    public int rank() {
        switch (this) {
            case ALL:
                return 3;
            case SELF_AND_CHILD:
                return 2;
            case APPOINT:
                return 1;
            case SELF:
            default:
                return 0;
        }
    }
}
