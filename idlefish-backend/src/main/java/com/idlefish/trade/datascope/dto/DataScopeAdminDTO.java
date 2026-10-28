package com.idlefish.trade.datascope.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据范围管理后台请求体（ITER-T05）。
 */
public class DataScopeAdminDTO {

    /** 临时授权：开放可见的机构集合 + 有效期。 */
    public static class TempGrantRequest {
        @NotNull(message = "授权机构集合不能为空")
        public List<Long> orgIds;

        @NotNull(message = "授权有效期不能为空")
        public LocalDateTime expiredAt;
    }

    /** 越权预览：目标机构。 */
    public static class PreviewRequest {
        @NotNull(message = "目标机构不能为空")
        public Long targetOrgId;
    }

    /** 归属迁移：源机构 → 目标机构。 */
    public static class MigrateRequest {
        @NotNull(message = "源机构不能为空")
        public Long fromOrgId;

        @NotNull(message = "目标机构不能为空")
        public Long toOrgId;
    }
}
