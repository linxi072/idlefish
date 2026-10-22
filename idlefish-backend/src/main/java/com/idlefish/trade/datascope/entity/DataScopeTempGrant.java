package com.idlefish.trade.datascope.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据范围临时授权（ITER-T05 / REQ-05）：超级管理员可向某管理员临时开放额外机构的数据可见范围。
 * 授权在有效期内、且状态为启用时生效；过期或撤销后自动失效，不污染该管理员角色本身的固定范围。
 */
@TableName("t_data_scope_temp_grant")
public class DataScopeTempGrant extends BaseEntity implements Serializable {

    /** 授权单号（GR 前缀，来自 {@link com.idlefish.trade.common.util.IdGenerator#grantNo}）。 */
    private String grantNo;
    /** 被授权管理员 ID（t_admin_user.id）。 */
    private Long adminId;
    /** 临时开放可见的机构 ID 集合，逗号分隔（与角色固定 dataOrgIds 合并）。 */
    private String orgIds;
    /** 有效期截止时间；超过即失效。 */
    private LocalDateTime expiredAt;
    /** 状态：1 启用 / 0 已撤销。 */
    private Integer status = 1;

    public String getGrantNo() {
        return grantNo;
    }

    public void setGrantNo(String grantNo) {
        this.grantNo = grantNo;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getOrgIds() {
        return orgIds;
    }

    public void setOrgIds(String orgIds) {
        this.orgIds = orgIds;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
