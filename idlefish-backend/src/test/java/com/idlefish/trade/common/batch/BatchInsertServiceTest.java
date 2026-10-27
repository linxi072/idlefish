package com.idlefish.trade.common.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BatchInsertService 端到端验证（真实 MySQL，@Transactional 回滚）：
 * 验证在 BATCH 执行器会话下循环 insert 能正确落库，且复用 BaseMapper.insert 链路，
 * 因此 IdType.ASSIGN_ID 雪花主键与 @TableField(fill=INSERT) 时间戳填充均生效。
 */
@SpringBootTest
@Transactional
class BatchInsertServiceTest {

    @Autowired
    private BatchInsertService batchInsertService;
    @Autowired
    private AdminUserRoleMapper adminUserRoleMapper;

    private static final long SENTINEL_ADMIN = 7770000001L;

    @Test
    void batchInsert_persistsAllRows_withIdAndFillApplied() {
        List<AdminUserRole> relations = new ArrayList<>();
        for (long roleId = 8801L; roleId <= 8803L; roleId++) {
            AdminUserRole r = new AdminUserRole();
            r.setAdminUserId(SENTINEL_ADMIN);
            r.setRoleId(roleId);
            relations.add(r);
        }

        int affected = batchInsertService.batchInsert(AdminUserRole.class, AdminUserRoleMapper.class, relations);
        assertEquals(3, affected);

        List<AdminUserRole> stored = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getAdminUserId, SENTINEL_ADMIN));
        assertEquals(3, stored.size());
        for (AdminUserRole r : stored) {
            assertNotNull(r.getId(), "ASSIGN_ID 雪花主键应已生成");
            assertNotNull(r.getCreatedAt(), "@TableField(fill=INSERT) 时间戳应已填充");
        }
    }

    @Test
    void batchInsert_emptyList_isNoOp() {
        int affected = batchInsertService.batchInsert(AdminUserRole.class, AdminUserRoleMapper.class, List.of());
        assertEquals(0, affected);
    }
}
