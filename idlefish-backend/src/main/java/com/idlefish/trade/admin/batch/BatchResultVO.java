package com.idlefish.trade.admin.batch;

import lombok.Data;

import java.util.List;

/**
 * 批量操作汇总结果（ITER-U03 REQ-02/REQ-04/REQ-05/REQ-11）。
 *
 * <p>前端据此展示进度与失败明细并可导出；{@code failedIds} 可直接回填勾选框做重试（REQ-05）。
 */
@Data
public class BatchResultVO {

    private int total;
    private int success;
    private int skipped;
    private int failed;
    /** 明细（成功/跳过/失败逐条，便于导出与重试）。 */
    private List<BatchItemResult> items;
    /** 失败 ID 列表：供一键重试。 */
    private List<String> failedIds;
    /** 审计日志 ID（操作留痕，REQ-11）。 */
    private Long auditLogId;

    public static BatchResultVO of(List<BatchItemResult> items, List<String> failedIds, Long auditLogId) {
        BatchResultVO vo = new BatchResultVO();
        vo.setItems(items);
        vo.setFailedIds(failedIds);
        vo.setAuditLogId(auditLogId);
        vo.setTotal(items.size());
        int s = 0;
        int k = 0;
        int f = 0;
        for (BatchItemResult r : items) {
            if (BatchItemResult.SUCCESS.equals(r.getOutcome())) {
                s++;
            } else if (BatchItemResult.SKIPPED.equals(r.getOutcome())) {
                k++;
            } else {
                f++;
            }
        }
        vo.setSuccess(s);
        vo.setSkipped(k);
        vo.setFailed(f);
        return vo;
    }

    /** 是否全部成功（无跳过无失败）。 */
    public boolean allSucceeded() {
        return success == total;
    }
}
