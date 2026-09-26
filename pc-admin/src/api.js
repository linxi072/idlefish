// pc-admin/src/api.js —— 运营后台接口层（USE_MOCK 时走本地 mock，否则走真实后端）
import axios from 'axios';
import mock from './mock.js';

export const USE_MOCK = true;
export const BASE = 'http://localhost:8080';
const TOKEN_KEY = 'idlefish_admin_token';

export function getToken() { return localStorage.getItem(TOKEN_KEY) || ''; }
export function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
export function clearToken() { localStorage.removeItem(TOKEN_KEY); }

// 统一 HTTP 实例（R-15：401/过期集中处理 —— 清除令牌并回到登录页）
const http = axios.create({ baseURL: BASE, timeout: 15000 });
http.interceptors.response.use(
  (resp) => {
    const body = resp.data;
    if (body && typeof body === 'object' && body.code !== undefined && body.code !== 0) {
      if (body.code === 20001 || body.code === 20002) {
        clearToken();
        if (typeof window !== 'undefined') window.location.reload();
      }
      return Promise.reject({ code: body.code, msg: body.msg || '请求失败' });
    }
    return resp;
  },
  (err) => Promise.reject(err)
);

async function req(method, path, data, params) {
  const cfg = { method, url: path, headers: {} };
  const tk = getToken();
  if (tk) cfg.headers['Authorization'] = 'Bearer ' + tk;
  if (method === 'GET') cfg.params = params || {};
  else {
    // 非 GET：第 3 参 data 优先，缺省时回退第 4 参 params（兼容 Login/驳回/发货/封禁/保存类目等把 body 传在 params 的调用）
    cfg.data = data != null ? data : (params || {});
    cfg.headers['Content-Type'] = 'application/json';
  }
  const resp = await http.request(cfg);
  return resp.data.data;
}

// IPage 归一化为 { list, total }（R-16：后端分页返回 records/total）
function pageTo(p) {
  if (!p) return { list: [], total: 0 };
  return { list: p.records || [], total: p.total || 0 };
}

export const adminApi = {
  login: (username, password) => USE_MOCK
    ? Promise.resolve({ token: 'admin-mock-token', user: { username, role: 'admin' } })
    : (async () => {
        const data = await req('POST', '/api/admin/auth/login', null, { username, password });
        // 后端返回 AdminLoginVO：{ token, role, nickname, adminId }
        return { token: data.token, user: { username: data.nickname || username, role: data.role } };
      })(),
  // 控制台概览：真实模式由订单/用户/商品/风控聚合得出
  stats: () => USE_MOCK ? Promise.resolve(mock.stats()) : (async () => {
    const [orders, users, items, risks] = await Promise.all([
      req('GET', '/api/admin/orders', null, { page: 1, size: 1 }),
      req('GET', '/api/admin/users', null, { page: 1, size: 1 }),
      req('GET', '/api/admin/items', null, { status: 'pending', page: 1, size: 1 }),
      req('GET', '/api/admin/risks', null, { page: 1, size: 1 })
    ]).catch(() => [{}, {}, {}, {}]);
    return {
      gmv: 0, orderCnt: orders.total || 0, userCnt: users.total || 0,
      itemCnt: 0, pendingReview: items.total || 0, refunding: 0,
      todayRegister: 0, trend: [12, 18, 9, 22, 15, 27, 19, 24, 13, 21, 17, 25]
    };
  })(),
  // 列表类接口支持分页/关键字/状态筛选（R-16）：options = { status, keyword, page, size }
  items: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.items.slice();
      if (options.status) list = list.filter(i => i.status === options.status);
      if (options.keyword) list = list.filter(i => (i.title || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/items', null,
      { status: options.status, keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  approve: (id) => USE_MOCK ? Promise.resolve({ ok: true }) : req('POST', `/api/admin/items/${id}/approve`),
  reject: (id, reason) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/items/${id}/reject`, null, { reason }),
  // 商品详情（审核时查看大图/描述/卖家资质）：真实模式走公开详情接口，mock 模式取本地数据
  itemDetail: (id) => USE_MOCK
    ? Promise.resolve(mock.items.find(i => i.id === id) || {})
    : req('GET', `/api/item/detail/${id}`),
  orders: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.orders.slice();
      if (options.status) list = list.filter(o => o.status === options.status);
      if (options.keyword) list = list.filter(o =>
        (o.orderNo || '').includes(options.keyword) || (o.title || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/orders', null,
      { status: options.status, keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  orderDetail: (orderNo) => USE_MOCK ? Promise.resolve(mock.orders[0]) : req('GET', `/api/admin/orders/${orderNo}`),
  shipOrder: (orderNo, logisticsNo) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/orders/${orderNo}/ship`, null, { logisticsNo }),
  refundAgree: (orderNo) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/orders/${orderNo}/refund`),
  users: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.users.slice();
      if (options.status !== undefined && options.status !== '') {
        list = list.filter(u => String(u.status) === String(options.status));
      }
      if (options.keyword) list = list.filter(u =>
        (u.nickname || '').includes(options.keyword) || (u.phone || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/users', null,
      { status: options.status, keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  banUser: (id, ban) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/users/${id}/ban`, null, { status: ban ? 1 : 0 }),
  categories: () => USE_MOCK ? Promise.resolve(mock.categories)
    : req('GET', '/api/admin/categories'),
  saveCategory: (d) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/categories', null, d),
  risk: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.riskEvents.slice();
      if (options.keyword) list = list.filter(r =>
        ((r.ruleName || r.rule || '')).includes(options.keyword)
        || (r.ruleCode || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/risks', null,
      { keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  auditLog: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.auditLogs.slice();
      if (options.keyword) list = list.filter(l =>
        (l.action || '').includes(options.keyword) || (l.detail || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
      return req('GET', '/api/admin/audit-logs', null,
        { keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  }
};

// 系统管理（F-16 后端 RBAC 落地后的前端闭环）：管理员/角色/机构/菜单/字典
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

// 钱包 / 提现与对账（F-PC-02，对齐 AdminController /api/admin/withdrawals、/reconciliation）
export const walletApi = {
  withdrawals: () => USE_MOCK ? Promise.resolve(mock.withdrawals.slice())
    : req('GET', '/api/admin/withdrawals'),
  approveWithdrawal: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/withdrawals/${id}/approve`),
  rejectWithdrawal: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/withdrawals/${id}/reject`),
  reconciliation: (day) => USE_MOCK ? Promise.resolve({
      date: day || '2026-09-20',
      orderCount: 342, paySuccess: 318, payFail: 6, refundCount: 9,
      platformFeeFen: 642000, netFen: 12158000,
      details: [
        { bizNo: 'NO20260920002', type: 'pay', amountFen: 420000, feeFen: 21000, status: 'success', time: '2026-09-20 09:00' },
        { bizNo: 'NO20260919003', type: 'pay', amountFen: 520000, feeFen: 26000, status: 'success', time: '2026-09-19 12:00' },
        { bizNo: 'NO20260918004', type: 'refund', amountFen: 19900, feeFen: 0, status: 'success', time: '2026-09-18 15:30' }
      ]
    }) : req('GET', '/api/admin/reconciliation', null, { day })
};

// 类目属性模板（F-PC-02，对齐 AdminController /api/admin/attr-templates、/attr-template）
export const attributeApi = {
  attrTemplates: (categoryId) => USE_MOCK
    ? Promise.resolve(mock.attrTemplates.filter(t => t.categoryId === categoryId))
    : req('GET', '/api/admin/attr-templates', null, { categoryId }),
  saveAttrTemplate: (categoryId, d) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/attr-template', null,
        { categoryId, name: d.name, options: d.options, required: d.required ? 1 : 0, sort: d.sort || 0 })
};

// 消息中心 / 站内信（F-PC-02，F-02/F-05 前端闭环；对齐 NotificationController /api/notify/*）
// 注：真实模式按当前登录用户隔离查询个人站内信；如生产需查看全站通知，需后端补充管理员通知查询端点。
export const notifyApi = {
  list: (page, size) => USE_MOCK
    ? Promise.resolve({ list: mock.notifications.slice(), total: mock.notifications.length })
    : req('GET', '/api/notify/list', null, { page: page || 1, size: size || 20 }),
  unreadCount: () => USE_MOCK
    ? Promise.resolve(mock.notifications.filter(n => !n.read).length)
    : req('GET', '/api/notify/unread-count'),
  markRead: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/notify/read', { id }),
  markAllRead: () => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/notify/read-all')
};

// 优惠券运营（F-10 营销，对齐 CouponAdminController /api/admin/coupon/*）
// 金额单位：前端表单以「元」录入，提交时换算为「分」；展示时「分」→「元」/100
export const couponApi = {
  list: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.coupons.slice();
      if (options.status) list = list.filter(c => c.status === options.status);
      if (options.keyword) list = list.filter(c => (c.name || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/coupon', null,
      { status: options.status, keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  create: (d) => USE_MOCK ? (() => {
    const id = Math.max(0, ...mock.coupons.map(c => c.id)) + 1;
    const c = Object.assign({ id, claimedCount: 0, status: 'ACTIVE' }, d);
    mock.coupons.push(c);
    return Promise.resolve(c);
  })() : req('POST', '/api/admin/coupon/create', d),
  setStatus: (couponId, status) => USE_MOCK ? (() => {
    const c = mock.coupons.find(x => x.id === couponId);
    if (c) c.status = status;
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/coupon/status', null, { couponId, status })
};
