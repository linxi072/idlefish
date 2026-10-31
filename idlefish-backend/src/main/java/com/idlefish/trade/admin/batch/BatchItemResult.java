package com.idlefish.trade.admin.batch;

import lombok.Data;

/**
 * 批量操作单项结果（ITER-U03 REQ-04/REQ-05）。
 *
 * <p>三类结局语义严格区分：
 * <ul>
 *   <li>{@link #SUCCESS}：本次真实生效，且已计入已生效集合（部分失败不回滚）。</li>
 *   <li>{@link #SKIPPED}：预检判定无需/不可处理（不存在、已删除、已是目标态），未产生副作用。</li>
 *   <li>{@link #FAILED}：尝试执行但被业务规则或状态机拒绝，需人工介入；ID 可直接用于重试。</li>
 * </ul>
 */
@Data
public class BatchItemResult {

    public static final String SUCCESS = "SUCCESS";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    /** 目标 ID。 */
    private String targetId;
    /** 结局：SUCCESS / SKIPPED / FAILED。 */
    private String outcome;
    /** 说明：跳过原因或失败原因（成功时为结果摘要）。 */
    private String reason;

    public static BatchItemResult success(String targetId, String reason) {
        BatchItemResult r = new BatchItemResult();
        r.setTargetId(targetId);
        r.setOutcome(SUCCESS);
        r.setReason(reason);
        return r;
    }

    public static BatchItemResult skipped(String targetId, String reason) {
        BatchItemResult r = new BatchItemResult();
        r.setTargetId(targetId);
        r.setOutcome(SKIPPED);
        r.setReason(reason);
        return r;
    }

    public static BatchItemResult failed(String targetId, String reason) {
        BatchItemResult r = new BatchItemResult();
        r.setTargetId(targetId);
        r.setOutcome(FAILED);
        r.setReason(reason);
        return r;
    }
}
