package com.idlefish.trade.admin.batch;

import lombok.Data;

/**
 * 批量操作影响面预览（ITER-U03 REQ-03）：二次确认前展示「条数与范围摘要」，
 * 让运营在确认弹窗中看到实际会被处理的量，而非仅看到勾选数。
 */
@Data
public class BatchPreviewVO {

    /** 勾选条数。 */
    private int selected;
    /** 实际存在（未删除）条数。 */
    private int existing;
    /** 待处理（未出结论）条数：本次真正会产生变更的量。 */
    private int actionable;
    /** 已通过条数（将被跳过）。 */
    private int alreadyPass;
    /** 已驳回条数（将被跳过）。 */
    private int alreadyReject;

    public static BatchPreviewVO of(int selected, int existing, int actionable, int alreadyPass, int alreadyReject) {
        BatchPreviewVO vo = new BatchPreviewVO();
        vo.setSelected(selected);
        vo.setExisting(existing);
        vo.setActionable(actionable);
        vo.setAlreadyPass(alreadyPass);
        vo.setAlreadyReject(alreadyReject);
        return vo;
    }

    /** 将被跳过的条数。 */
    public int skipped() {
        return selected - actionable;
    }
}
