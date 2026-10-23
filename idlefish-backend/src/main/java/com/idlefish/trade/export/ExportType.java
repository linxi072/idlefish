package com.idlefish.trade.export;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;

import java.util.Arrays;

/**
 * 可导出数据类型（ITER-T06 运营数据导出中心）。
 * <ul>
 *   <li>{@code hasOrgScope} 标识该实体是否带 {@code org_id} 列：</li>
 *   <ul>
 *     <li>true（ADMIN_USER / ITEM）：导出受数据范围（ITER-T05）约束，仅可见机构数据可被导出；</li>
 *     <li>false（USER / ORDER）：无 org_id 列，仅「不受限（ALL）」管理员可导出，防跨机构数据泄露（REQ-07）。</li>
 *   </ul>
 * </ul>
 */
public enum ExportType {

    ADMIN_USER("admin_user", "管理员账号", true),
    ITEM("item", "商品", true),
    USER("user", "用户", false),
    ORDER("order", "订单", false);

    private final String code;
    private final String label;
    private final boolean hasOrgScope;

    ExportType(String code, String label, boolean hasOrgScope) {
        this.code = code;
        this.label = label;
        this.hasOrgScope = hasOrgScope;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isHasOrgScope() {
        return hasOrgScope;
    }

    /** 由契约/入参 code 解析枚举；非法值抛出 EXPORT_TYPE_UNSUPPORTED。 */
    public static ExportType fromCode(String code) {
        if (code == null) {
            throw new BizException(Code.EXPORT_TYPE_UNSUPPORTED);
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BizException(Code.EXPORT_TYPE_UNSUPPORTED));
    }
}
