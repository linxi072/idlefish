package com.idlefish.trade.acceptance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 真机验收用例（ITER-F01 / F-11.5 验收闭环管理）。
 * <p>
 * 用例即 PRD「第十节 验收」所列 REQ-01~REQ-09 的逐条追踪项；真机环境由运营/测试在
 * 真机或商户号侧执行，结果回填本表。本实体只承载「验收追踪 + 结果记录」，不对支付/出款
 * 等已落地的真实逻辑做重复实现（REQ-01~REQ-08 由 PayService / WithdrawalService /
 * FundEscrowService 提供，本模块仅 {@code 回链} 与 {@code 闭环编排}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_acceptance_case")
public class AcceptanceCase extends BaseEntity {

    /** 关联需求编号：REQ-01 ~ REQ-09（与 PRD 验收小节一一对应）。 */
    private String reqCode;

    /** 用例标题（如「支付回调 v3 验签恒拒绝伪造」）。 */
    private String title;

    /** 分类：PAY / IM / SIGN / CONFIG / DEFECT（便于后台按域聚合）。 */
    private String category;

    /** 验收状态：NOT_RUN(未执行) / PASSED(通过) / FAILED(未通过) / SKIPPED(沙箱不适用，已确认跳过)。 */
    private String status;

    /** 验收执行人（真机/商户号侧操作的运营或测试）。 */
    private String executor;

    /** 结果备注（失败原因、真机环境说明等）。 */
    private String resultNote;

    /** 验收执行时间（回填时的时间戳）。 */
    private LocalDateTime executedAt;

    /** 关联 PRD 文档标识（默认 {@code PRD-P0-F01-真机验收闭环}）。 */
    private String prdLink;
}
