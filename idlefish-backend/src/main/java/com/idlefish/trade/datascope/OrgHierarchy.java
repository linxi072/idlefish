package com.idlefish.trade.datascope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机构层级（树）。基于 {@link OrgRef} 列表构建森林索引，支持多根与环状防御。
 * <p>纯函数：构造时建索引，{@link #descendantsOf(long)} 无副作用，可重复调用。</p>
 */
public class OrgHierarchy {

    private final Map<Long, List<Long>> childrenByParent = new HashMap<>();
    private final Set<Long> allIds = new HashSet<>();

    public OrgHierarchy(List<OrgRef> refs) {
        List<OrgRef> safe = refs == null ? Collections.emptyList() : refs;
        for (OrgRef r : safe) {
            allIds.add(r.id());
            if (r.parentId() != OrgRef.ROOT_PARENT) {
                childrenByParent.computeIfAbsent(r.parentId(), k -> new ArrayList<>()).add(r.id());
            }
        }
    }

    /**
     * 返回 {@code rootId} 及其全部后代（含自身），已去重并按先根遍历顺序。
     * 环状结构下通过 visited 集合安全终止，不栈溢出。
     * 若 {@code rootId} 不存在于层级中，返回空列表。
     */
    public List<Long> descendantsOf(long rootId) {
        List<Long> result = new ArrayList<>();
        if (!allIds.contains(rootId)) {
            return result;
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (!visited.add(cur)) {
                continue; // 已访问（环状防御）
            }
            result.add(cur);
            // 逆序入栈，保证出栈顺序与子节点声明顺序一致（先根遍历）
            List<Long> kids = childrenByParent.getOrDefault(cur, Collections.emptyList());
            for (int i = kids.size() - 1; i >= 0; i--) {
                Long kid = kids.get(i);
                if (!visited.contains(kid)) {
                    stack.push(kid);
                }
            }
        }
        return result;
    }

    /** 是否包含该机构。 */
    public boolean contains(long id) {
        return allIds.contains(id);
    }

    /** 全部机构 id（只读视图）。 */
    public Set<Long> allIds() {
        return Collections.unmodifiableSet(allIds);
    }
}
