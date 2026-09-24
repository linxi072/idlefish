package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.vo.TreeVO;
import com.idlefish.trade.system.entity.RoleMenu;
import com.idlefish.trade.system.entity.SysMenu;
import com.idlefish.trade.system.mapper.RoleMenuMapper;
import com.idlefish.trade.system.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单/权限服务（PC 系统管理）：菜单树 CRUD + 角色权限可见性。
 * type：0 目录 / 1 菜单 / 2 按钮；perms 为权限标识（如 system:user:list）。
 */
@Service
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public SysMenuService(SysMenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    public List<SysMenu> list() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
    }

    public List<TreeVO> tree() {
        return buildTree(list(), 0L);
    }

    private List<TreeVO> buildTree(List<SysMenu> all, Long parentId) {
        List<TreeVO> res = new ArrayList<>();
        for (SysMenu m : all) {
            if (parentId.equals(m.getParentId())) {
                TreeVO n = new TreeVO();
                n.setId(m.getId());
                n.setParentId(m.getParentId());
                n.setName(m.getName());
                n.setSort(m.getSort());
                n.getMeta().put("type", m.getType());
                n.getMeta().put("path", m.getPath());
                n.getMeta().put("component", m.getComponent());
                n.getMeta().put("icon", m.getIcon());
                n.getMeta().put("perms", m.getPerms());
                n.getMeta().put("status", m.getStatus());
                n.setChildren(buildTree(all, m.getId()));
                res.add(n);
            }
        }
        return res;
    }

    public SysMenu get(Long id) {
        return menuMapper.selectById(id);
    }

    public void save(SysMenu e) {
        menuMapper.insert(e);
    }

    public void update(SysMenu e) {
        menuMapper.updateById(e);
    }

    public void remove(Long id) {
        removeChildren(id);
        menuMapper.deleteById(id);
    }

    private void removeChildren(Long parentId) {
        List<SysMenu> children = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, parentId));
        for (SysMenu c : children) {
            roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, c.getId()));
            removeChildren(c.getId());
            menuMapper.deleteById(c.getId());
        }
    }
}
