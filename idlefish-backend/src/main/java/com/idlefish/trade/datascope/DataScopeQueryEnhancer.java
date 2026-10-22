package com.idlefish.trade.datascope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 数据范围查询增强器（ITER-T05 / REQ-02/REQ-04）：在统一 SQL 过滤层（{@code DataPermissionInterceptor}）
 * 之外，为「无法走拦截器」的场景提供两类兜底：
 * <ul>
 *   <li>1) 内存过滤：对已经加载到内存的集合按 orgId 成员关系过滤（如非 SQL 聚合结果、第三方回查）。</li>
 *   <li>2) 编程式 wrapper 注入：在业务代码已知范围时，向 {@link LambdaQueryWrapper} 注入 {@code org_id IN (...)}。</li>
 * </ul>
 * 两者均遵循同一从严原则：{@code orgIds == null} 表示不受限（ALL），放行全部。
 */
public final class DataScopeQueryEnhancer {

    private DataScopeQueryEnhancer() {
    }

    /**
     * 内存过滤：保留 orgId ∈ orgIds 的行；orgIds 为 null 表示不受限，原样返回。
     */
    public static <T> List<T> filterInMemory(List<T> data, Function<T, Long> orgIdGetter, Set<Long> orgIds) {
        if (data == null || orgIds == null) {
            return data;
        }
        List<T> result = new ArrayList<>();
        for (T row : data) {
            Long orgId = orgIdGetter.apply(row);
            if (orgId != null && orgIds.contains(orgId)) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 编程式 wrapper 注入：向 MyBatis-Plus {@link LambdaQueryWrapper} 追加 {@code org_id IN (...)}。
     * orgIds 为 null（ALL）时不注入，保持原有语义。
     */
    public static <T> void applyOrgIn(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> orgIdColumn, Set<Long> orgIds) {
        if (wrapper == null || orgIds == null || orgIds.isEmpty()) {
            return;
        }
        wrapper.in(orgIdColumn, orgIds);
    }
}
