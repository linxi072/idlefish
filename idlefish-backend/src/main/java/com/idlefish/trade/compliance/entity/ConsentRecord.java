package com.idlefish.trade.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 个人信息收集授权记录（ITER-U05 REQ-07/REQ-08）：按 (user_id, consent_code) 唯一。
 *
 * <p>granted=1 表示已授权（记录 grantedAt）；撤回后置 granted=0 并记录 revokedAt，
 * 保留历史而非物理删除，便于合规审计追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_consent")
public class ConsentRecord extends BaseEntity {

    private Long userId;
    /** 授权项编码，见 {@link com.idlefish.trade.compliance.ConsentItem#getCode()}。 */
    private String consentCode;
    /** 是否已授权：0 已撤回 / 1 已授权。 */
    private Integer granted;
    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;
}
