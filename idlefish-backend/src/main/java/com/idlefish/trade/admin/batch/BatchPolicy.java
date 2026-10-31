package com.idlefish.trade.admin.batch;

/**
 * 批量操作策略（ITER-U03 REQ-03/REQ-05）：纯函数，无外部依赖，离线可单测。
 *
 * <p>口径：
 * <ul>
 *   <li>单次批量上限 {@link #MAX_BATCH_SIZE}，超出一律拒绝（防误操作放大与长事务）。</li>
 *   <li>高风险动作（封禁/删除/改价）必须显式二次确认 {@code confirmed=true}，否则拒绝（REQ-03）。</li>
 *   <li>部分失败不回滚：已成功项保持生效，失败项可凭 ID 重试（REQ-05）。</li>
 * </ul>
 */
public final class BatchPolicy {

    /** 单次批量上限。 */
    public static final int MAX_BATCH_SIZE = 200;

    private BatchPolicy() {
    }

    /** 受支持的批量动作：声明所需权限与风险等级。 */
    public enum Action {
        ITEM_AUDIT("item:audit", "商品审核", false),
        USER_BAN("user:ban", "用户封禁/解封", true);

        private final String permission;
        private final String desc;
        /** true 表示高风险：强制二次确认（REQ-03）。 */
        private final boolean highRisk;

        Action(String permission, String desc, boolean highRisk) {
            this.permission = permission;
            this.desc = desc;
            this.highRisk = highRisk;
        }

        public String getPermission() {
            return permission;
        }

        public String getDesc() {
            return desc;
        }

        public boolean isHighRisk() {
            return highRisk;
        }
    }

    /** 批量条数校验：空列表 / 超上限均拒绝，返回可读原因；合法返回 null。 */
    public static String sizeViolation(int size) {
        if (size <= 0) {
            return "批量目标不能为空";
        }
        if (size > MAX_BATCH_SIZE) {
            return "单次批量上限为 " + MAX_BATCH_SIZE + " 条，当前 " + size + " 条";
        }
        return null;
    }

    /** 该动作是否要求显式二次确认（REQ-03）。 */
    public static boolean requiresConfirm(Action action) {
        return action != null && action.isHighRisk();
    }

    /** 确认闸门：高风险且未确认 → 拒绝原因；否则 null 表示放行。 */
    public static String confirmViolation(Action action, Boolean confirmed) {
        if (requiresConfirm(action) && !Boolean.TRUE.equals(confirmed)) {
            return "「" + action.getDesc() + "」为高风险操作，须二次确认后执行";
        }
        return null;
    }
}
