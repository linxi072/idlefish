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
  else { cfg.data = data || {}; cfg.headers['Content-Type'] = 'application/json'; }
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
