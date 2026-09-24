package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.vo.TreeVO;
import com.idlefish.trade.system.entity.SysOrganization;
import com.idlefish.trade.system.mapper.SysOrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 机构/部门服务（PC 系统管理）：树形 CRUD。
 */
@Service
public class SysOrganizationService {

    private final SysOrganizationMapper mapper;

    public SysOrganizationService(SysOrganizationMapper mapper) {
        this.mapper = mapper;
    }

    public List<SysOrganization> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysOrganization>().orderByAsc(SysOrganization::getSort));
    }

    public List<TreeVO> tree() {
        return buildTree(listAll(), 0L);
    }

    private List<TreeVO> buildTree(List<SysOrganization> all, Long parentId) {
        List<TreeVO> res = new ArrayList<>();
        for (SysOrganization o : all) {
            if (parentId.equals(o.getParentId())) {
                TreeVO n = new TreeVO();
                n.setId(o.getId());
                n.setParentId(o.getParentId());
                n.setName(o.getName());
                n.setSort(o.getSort());
                n.getMeta().put("code", o.getCode());
                n.getMeta().put("level", o.getLevel());
                n.getMeta().put("leader", o.getLeader());
                n.getMeta().put("phone", o.getPhone());
                n.getMeta().put("status", o.getStatus());
                n.setChildren(buildTree(all, o.getId()));
                res.add(n);
            }
        }
        return res;
    }

    public SysOrganization get(Long id) {
        return mapper.selectById(id);
    }

    public void save(SysOrganization e) {
        mapper.insert(e);
    }

    public void update(SysOrganization e) {
        mapper.updateById(e);
    }

    public void remove(Long id) {
        long children = mapper.selectCount(
                new LambdaQueryWrapper<SysOrganization>().eq(SysOrganization::getParentId, id));
        if (children > 0) {
            throw new BizException(Code.BIZ_ERROR, "该机构下存在子机构，无法删除");
        }
        mapper.deleteById(id);
    }
}
