package com.idlefish.trade.datascope.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据范围管理后台请求体（ITER-T05）。
 */
public class DataScopeAdminDTO {

    /** 临时授权：开放可见的机构集合 + 有效期。 */
    public static class TempGrantRequest {
        public List<Long> orgIds;
        public LocalDateTime expiredAt;
    }

    /** 越权预览：目标机构。 */
    public static class PreviewRequest {
        public Long targetOrgId;
    }

    /** 归属迁移：源机构 → 目标机构。 */
    public static class MigrateRequest {
        public Long fromOrgId;
        public Long toOrgId;
    }
}
