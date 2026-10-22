package com.idlefish.trade.datascope;

/**
 * 机构引用（树边）：{@code id → parentId}。{@code parentId=0} 表示根节点。
 * 纯数据载体，无副作用。
 */
public record OrgRef(long id, long parentId) {

    /** 根节点父标识（无父机构）。 */
    public static final long ROOT_PARENT = 0L;

    public OrgRef {
        if (id == parentId) {
            // 自环（id==parentId）视为非法，强制收敛到根父以避免污染层级
            parentId = ROOT_PARENT;
        }
    }
}
