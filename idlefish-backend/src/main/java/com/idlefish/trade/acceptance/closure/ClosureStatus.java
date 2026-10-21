package com.idlefish.trade.acceptance.closure;

/**
 * 验收闭环状态（ITER-F01 纯函数产出）。
 * <p>
 * 状态机语义（由 {@link AcceptanceClosureCalculator} 依据用例与缺陷推导）：
 * <ul>
 *   <li>{@code NOT_RUN}    —— 尚未登记任何验收用例（验收未开始）。</li>
 *   <li>{@code OPEN_CASES} —— 存在未通过用例（NOT_RUN / FAILED）。</li>
 *   <li>{@code OPEN_DEFECTS} —— 用例均通过，但仍有未关闭缺陷（OPEN / FIXED）。</li>
 *   <li>{@code CLOSED}     —— 全部用例通过且无未关闭缺陷，验收闭环。</li>
 * </ul>
 */
public enum ClosureStatus {

    NOT_RUN("验收未开始", false),
    OPEN_CASES("存在未通过用例", false),
    OPEN_DEFECTS("存在未关闭缺陷", false),
    CLOSED("验收已闭环", true);

    private final String label;
    private final boolean closable;

    ClosureStatus(String label, boolean closable) {
        this.label = label;
        this.closable = closable;
    }

    public String getLabel() {
        return label;
    }

    /** 是否允许对验收单执行「关单」操作。 */
    public boolean isClosable() {
        return closable;
    }
}
