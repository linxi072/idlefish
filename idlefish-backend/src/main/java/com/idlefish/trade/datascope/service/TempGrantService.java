package com.idlefish.trade.datascope.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.datascope.entity.DataScopeTempGrant;
import com.idlefish.trade.datascope.mapper.DataScopeTempGrantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围临时授权服务（ITER-T05 / REQ-05）。
 * <p>临时授权用于超级管理员向某管理员临时开放额外机构可见范围，独立于角色固定范围，
 * 过期/撤销后自动失效，不污染角色配置。</p>
 */
@Service
public class TempGrantService {

    private final DataScopeTempGrantMapper grantMapper;

    public TempGrantService(DataScopeTempGrantMapper grantMapper) {
        this.grantMapper = grantMapper;
    }

    /** 纯函数：授权是否当前有效（启用且未过期）。 */
    public boolean isEffective(DataScopeTempGrant grant, LocalDateTime now) {
        if (grant == null || grant.getStatus() == null || grant.getStatus() != 1) {
            return false;
        }
        return grant.getExpiredAt() != null && grant.getExpiredAt().isAfter(now);
    }

    /** 纯函数：把临时授权开放的组织合并进基础范围；baseOrgIds 为 null（不受限）时直接返回 null。 */
    public Set<Long> effectiveOrgIds(Set<Long> baseOrgIds, List<DataScopeTempGrant> grants, LocalDateTime now) {
        if (baseOrgIds == null) {
            return null;
        }
        Set<Long> merged = new LinkedHashSet<>(baseOrgIds);
        if (grants != null) {
            for (DataScopeTempGrant g : grants) {
                if (isEffective(g, now)) {
                    for (Long orgId : parseOrgIds(g.getOrgIds())) {
                        merged.add(orgId);
                    }
                }
            }
        }
        return merged;
    }

    /** 创建临时授权（生成单号、落库）。 */
    public DataScopeTempGrant create(long adminId, Set<Long> orgIds, LocalDateTime expiredAt) {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setGrantNo(IdGenerator.grantNo());
        g.setAdminId(adminId);
        g.setOrgIds(String.join(",", orgIds.stream().sorted().map(String::valueOf).toList()));
        g.setExpiredAt(expiredAt);
        g.setStatus(1);
        grantMapper.insert(g);
        return g;
    }

    /** 撤销授权（幂等：已撤销不影响）。 */
    public void revoke(String grantNo) {
        DataScopeTempGrant g = grantMapper.selectOne(
                new LambdaQueryWrapper<DataScopeTempGrant>().eq(DataScopeTempGrant::getGrantNo, grantNo));
        if (g != null && g.getStatus() != null && g.getStatus() == 1) {
            g.setStatus(0);
            grantMapper.updateById((DataScopeTempGrant) g);
        }
    }

    /** 列出某管理员当前生效中的授权（启用且未过期）。 */
    public List<DataScopeTempGrant> listActive(long adminId, LocalDateTime now) {
        List<DataScopeTempGrant> all = grantMapper.selectList(
                new LambdaQueryWrapper<DataScopeTempGrant>()
                        .eq(DataScopeTempGrant::getAdminId, adminId)
                        .eq(DataScopeTempGrant::getStatus, 1));
        List<DataScopeTempGrant> active = new java.util.ArrayList<>();
        for (DataScopeTempGrant g : all) {
            if (isEffective(g, now)) {
                active.add(g);
            }
        }
        return active;
    }

    /** 解析逗号分隔的机构 ID 串（容错：跳过空白/非法）。 */
    public static Set<Long> parseOrgIds(String csv) {
        Set<Long> set = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                try {
                    set.add(Long.parseLong(t));
                } catch (NumberFormatException ignore) {
                    // 跳过非法片段，保证解析健壮性
                }
            }
        }
        return set;
    }
}
