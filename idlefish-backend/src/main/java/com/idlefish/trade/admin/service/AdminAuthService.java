package com.idlefish.trade.admin.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 权限骨架（PRD §7 运营后台）：
 * 角色 → 权限集合；接口按 action 校验权限。
 * 生产建议接入统一权限中心 / Spring Security。
 */
@Service
public class AdminAuthService {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        ROLE_PERMISSIONS.put("SUPER", new HashSet<>(Set.of(
                "item:audit", "item:manage", "order:view", "order:refund",
                "user:view", "user:ban", "category:manage", "risk:view", "audit:log")));
        ROLE_PERMISSIONS.put("OPERATOR", new HashSet<>(Set.of(
                "item:audit", "order:view", "user:view", "category:manage", "risk:view")));
        ROLE_PERMISSIONS.put("FINANCE", new HashSet<>(Set.of(
                "order:view", "order:refund", "audit:log")));
    }

    /** 校验角色是否具备某权限，不具备则抛异常。 */
    public void require(String role, String action) {
        Set<String> perms = ROLE_PERMISSIONS.get(role);
        if (perms == null || !perms.contains(action)) {
            throw new BizException(Code.FORBIDDEN, "角色 " + role + " 无权限 " + action);
        }
    }
}
