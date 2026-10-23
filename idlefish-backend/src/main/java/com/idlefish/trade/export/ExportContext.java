package com.idlefish.trade.export;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 导出上下文（不可变）：由 {@link ExportService} 在范围校验后产出，供各 {@link ExportProvider} 取数。
 * <ul>
 *   <li>{@code limit}：本次导出行数上限；</li>
 *   <li>{@code orgIds}：生效的可见机构集合；{@code null} 表示不受限（导出全量）。</li>
 * </ul>
 */
public class ExportContext {

    private final int limit;
    private final Set<Long> orgIds;

    public ExportContext(int limit, Set<Long> orgIds) {
        this.limit = limit;
        this.orgIds = orgIds == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(orgIds));
    }

    public int getLimit() {
        return limit;
    }

    /** 可见机构集合；{@code null} 表示不受限（导出全量）。 */
    public Set<Long> getOrgIds() {
        return orgIds;
    }

    public boolean isUnrestricted() {
        return orgIds == null;
    }
}
