import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const adminApi = {
  login: (username, password) => USE_MOCK
    ? Promise.resolve({ token: 'admin-mock-token', user: { username, role: 'admin' } })
    : (async () => {
        const data = await req('POST', '/api/admin/auth/login', null, { username, password });
        // 后端返回 AdminLoginVO：{ token, role, nickname, adminId }
        return { token: data.token, user: { username: data.nickname || username, role: data.role } };
      })(),
  // 当前管理员（D-18 路由守卫支撑）：初始化时校验令牌有效性，无效/过期返回 401/403
  me: () => USE_MOCK
    ? Promise.resolve({ token: 'admin-mock-token', user: { username: 'admin', role: 'SUPER' } })
    : req('GET', '/api/admin/auth/me').then(d => ({
        token: d.token, user: { username: d.nickname || 'admin', role: d.role }
      })),
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
  // 物流轨迹（F-14.5 结构化时间轴：companyName/statusText/tracks[]，tracks 节点含 type 着色）
  logisticsTrack: (orderNo) => USE_MOCK ? Promise.resolve(mock.logisticsTrack(orderNo))
    : req('GET', `/api/admin/orders/${orderNo}/logistics`),
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
  },
  riskRules: () => USE_MOCK ? Promise.resolve(mock.riskRules.slice())
    : req('GET', '/api/admin/risk/rules'),
  saveRiskRules: (rules) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('PUT', '/api/admin/risk/rules', rules)
};
