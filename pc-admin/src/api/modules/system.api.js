import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const systemApi = {
  // ===== 管理员 =====
  adminUsers: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.adminUsers.slice();
      if (options.keyword) list = list.filter(u => (u.username || '').includes(options.keyword) || (u.nickname || '').includes(options.keyword));
      if (options.status !== undefined && options.status !== '' && options.status !== null) list = list.filter(u => String(u.status) === String(options.status));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/system/user', null, { keyword: options.keyword, status: options.status, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  saveAdminUser: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/user/' + d.id, d) : req('POST', '/api/admin/system/user', d)),
  deleteAdminUser: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/user/' + id),
  assignRoles: (adminUserId, roleIds) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/system/user/assign-roles', { adminUserId, roleIds }),
  userRoles: (adminUserId) => USE_MOCK ? Promise.resolve((mock.adminUsers.find(u => u.id === adminUserId) || {}).roleIds || [])
    : req('GET', '/api/admin/system/user/' + adminUserId + '/roles'),
  resetPassword: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('POST', '/api/admin/system/user/' + id + '/reset-password'),

  // ===== 角色 =====
  roles: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.roles.slice();
      if (options.keyword) list = list.filter(r => (r.name || '').includes(options.keyword) || (r.code || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/system/role', null, { keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  saveRole: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/role/' + d.id, d) : req('POST', '/api/admin/system/role', d)),
  deleteRole: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/role/' + id),
  roleMenus: (roleId) => USE_MOCK ? Promise.resolve((mock.roles.find(r => r.id === roleId) || {}).menuIds || [])
    : req('GET', '/api/admin/system/role/' + roleId + '/menus'),
  assignMenus: (roleId, menuIds) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/system/role/assign-menus', { roleId, menuIds }),

  // ===== 机构 =====
  orgTree: () => USE_MOCK ? Promise.resolve(mock.orgTree) : req('GET', '/api/admin/system/organization/tree'),
  saveOrg: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/organization/' + d.id, d) : req('POST', '/api/admin/system/organization', d)),
  deleteOrg: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/organization/' + id),

  // ===== 菜单 =====
  menuTree: () => USE_MOCK ? Promise.resolve(mock.menuTree) : req('GET', '/api/admin/system/menu/tree'),
  saveMenu: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/menu/' + d.id, d) : req('POST', '/api/admin/system/menu', d)),
  deleteMenu: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/menu/' + id),

  // ===== 字典 =====
  dictTypes: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.dictTypes.slice();
      if (options.keyword) list = list.filter(t => (t.name || '').includes(options.keyword) || (t.type || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/system/dict/types', null, { keyword: options.keyword }).then(pageTo);
  },
  saveDictType: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/dict/type/' + d.id, d) : req('POST', '/api/admin/system/dict/type', d)),
  deleteDictType: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/dict/type/' + id),
  dictData: (type) => USE_MOCK ? Promise.resolve(mock.dictData.filter(d => d.type === type))
    : req('GET', '/api/admin/system/dict/data', null, { type }),
  saveDictData: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : (d.id ? req('PUT', '/api/admin/system/dict/data/' + d.id, d) : req('POST', '/api/admin/system/dict/data', d)),
  deleteDictData: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('DELETE', '/api/admin/system/dict/data/' + id),
  dictDropdown: (type) => USE_MOCK
    ? Promise.resolve(mock.dictData.filter(d => d.type === type && d.status === 0).map(d => ({ label: d.label, value: d.value })))
    : req('GET', '/api/admin/system/dict/dropdown', null, { type })
};
