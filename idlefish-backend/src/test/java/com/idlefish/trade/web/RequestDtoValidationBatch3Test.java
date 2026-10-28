package com.idlefish.trade.web;

import com.idlefish.trade.datascope.dto.DataScopeAdminDTO;
import com.idlefish.trade.export.ExportQuery;
import com.idlefish.trade.favorite.controller.FavoriteController;
import com.idlefish.trade.notify.controller.NotificationController;
import com.idlefish.trade.system.dto.AssignDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批3 涉及的 8 个纯请求 DTO 字段级约束正确性验证（标准 jakarta {@link Validator}，离线、不依赖 Spring/MySQL）：
 * <ul>
 *   <li>AssignDTO：id / ids 必填；</li>
 *   <li>ExportQuery：type 必填（非空）；</li>
 *   <li>DataScopeAdminDTO 三内嵌类：TempGrantRequest(orgIds/expiredAt)、PreviewRequest(targetOrgId)、
 *       MigrateRequest(fromOrgId/toOrgId) 全部必填；</li>
 *   <li>NotificationController.ReadDTO：id 必填；</li>
 *   <li>FavoriteController.ToggleDTO：itemId 必填。</li>
 * </ul>
 * 非法实例应产生约束违例（不被漏校验）；合法实例应零违例（不被 400 误杀）。
 */
class RequestDtoValidationBatch3Test {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private <T> Set<ConstraintViolation<T>> v(T obj) {
        return validator.validate(obj);
    }

    // ===== AssignDTO：id / ids 必填 =====
    @Test
    void assign_nullId_hasViolation() {
        AssignDTO d = new AssignDTO();
        d.setIds(List.of(1L));
        assertFalse(v(d).isEmpty());
    }

    @Test
    void assign_nullIds_hasViolation() {
        AssignDTO d = new AssignDTO();
        d.setId(1L);
        assertFalse(v(d).isEmpty());
    }

    @Test
    void assign_valid_noViolation() {
        AssignDTO d = new AssignDTO();
        d.setId(1L);
        d.setIds(List.of(1L, 2L));
        assertTrue(v(d).isEmpty());
    }

    // ===== ExportQuery：type 必填（非空） =====
    @Test
    void exportQuery_blankType_hasViolation() {
        ExportQuery q = new ExportQuery();
        q.setType("   ");
        assertFalse(v(q).isEmpty());
    }

    @Test
    void exportQuery_valid_noViolation() {
        ExportQuery q = new ExportQuery();
        q.setType("USER");
        assertTrue(v(q).isEmpty());
    }

    // ===== DataScopeAdminDTO.TempGrantRequest：orgIds / expiredAt 必填 =====
    @Test
    void tempGrant_nullOrgIds_hasViolation() {
        DataScopeAdminDTO.TempGrantRequest r = new DataScopeAdminDTO.TempGrantRequest();
        r.expiredAt = LocalDateTime.now().plusDays(1);
        assertFalse(v(r).isEmpty());
    }

    @Test
    void tempGrant_nullExpiredAt_hasViolation() {
        DataScopeAdminDTO.TempGrantRequest r = new DataScopeAdminDTO.TempGrantRequest();
        r.orgIds = List.of(1L);
        assertFalse(v(r).isEmpty());
    }

    @Test
    void tempGrant_valid_noViolation() {
        DataScopeAdminDTO.TempGrantRequest r = new DataScopeAdminDTO.TempGrantRequest();
        r.orgIds = List.of(1L, 2L);
        r.expiredAt = LocalDateTime.now().plusDays(1);
        assertTrue(v(r).isEmpty());
    }

    // ===== DataScopeAdminDTO.PreviewRequest：targetOrgId 必填 =====
    @Test
    void preview_nullTargetOrgId_hasViolation() {
        DataScopeAdminDTO.PreviewRequest r = new DataScopeAdminDTO.PreviewRequest();
        assertFalse(v(r).isEmpty());
    }

    @Test
    void preview_valid_noViolation() {
        DataScopeAdminDTO.PreviewRequest r = new DataScopeAdminDTO.PreviewRequest();
        r.targetOrgId = 1L;
        assertTrue(v(r).isEmpty());
    }

    // ===== DataScopeAdminDTO.MigrateRequest：fromOrgId / toOrgId 必填 =====
    @Test
    void migrate_nullFromOrgId_hasViolation() {
        DataScopeAdminDTO.MigrateRequest r = new DataScopeAdminDTO.MigrateRequest();
        r.toOrgId = 2L;
        assertFalse(v(r).isEmpty());
    }

    @Test
    void migrate_valid_noViolation() {
        DataScopeAdminDTO.MigrateRequest r = new DataScopeAdminDTO.MigrateRequest();
        r.fromOrgId = 1L;
        r.toOrgId = 2L;
        assertTrue(v(r).isEmpty());
    }

    // ===== NotificationController.ReadDTO：id 必填 =====
    @Test
    void read_nullId_hasViolation() {
        NotificationController.ReadDTO d = new NotificationController.ReadDTO();
        assertFalse(v(d).isEmpty());
    }

    @Test
    void read_valid_noViolation() {
        NotificationController.ReadDTO d = new NotificationController.ReadDTO();
        d.setId(1L);
        assertTrue(v(d).isEmpty());
    }

    // ===== FavoriteController.ToggleDTO：itemId 必填 =====
    @Test
    void toggle_nullItemId_hasViolation() {
        FavoriteController.ToggleDTO d = new FavoriteController.ToggleDTO();
        assertFalse(v(d).isEmpty());
    }

    @Test
    void toggle_valid_noViolation() {
        FavoriteController.ToggleDTO d = new FavoriteController.ToggleDTO();
        d.setItemId(1L);
        assertTrue(v(d).isEmpty());
    }
}
