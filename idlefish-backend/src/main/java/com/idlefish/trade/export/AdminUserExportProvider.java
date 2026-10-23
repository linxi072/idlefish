package com.idlefish.trade.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理员账号导出（ITER-T06）：带 {@code org_id}，受数据范围约束（仅可见机构可被导出）。
 */
@Component
public class AdminUserExportProvider implements ExportProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminUserMapper adminUserMapper;

    public AdminUserExportProvider(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public ExportType type() {
        return ExportType.ADMIN_USER;
    }

    @Override
    public boolean hasOrgScope() {
        return true;
    }

    @Override
    public List<String> headers() {
        return Arrays.asList("id", "username", "nickname", "role", "orgId", "status", "createdAt");
    }

    @Override
    public List<List<String>> rows(ExportContext ctx) {
        LambdaQueryWrapper<AdminUser> w = new LambdaQueryWrapper<>();
        if (!ctx.isUnrestricted() && ctx.getOrgIds() != null && !ctx.getOrgIds().isEmpty()) {
            w.in(AdminUser::getOrgId, ctx.getOrgIds());
        }
        w.last("LIMIT " + ctx.getLimit());
        List<AdminUser> list = adminUserMapper.selectList(w);
        List<List<String>> rows = new ArrayList<>(list.size());
        for (AdminUser u : list) {
            rows.add(Arrays.asList(
                    str(u.getId()),
                    str(u.getUsername()),
                    str(u.getNickname()),
                    str(u.getRole()),
                    str(u.getOrgId()),
                    str(u.getStatus()),
                    fmt(u.getCreatedAt())));
        }
        return rows;
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }
}
