// api.js —— 统一接口层：useMock 时走本地 mock，否则走真实后端 REST
const http = require('./request.js');
const mock = require('./mock.js');
const store = require('./store.js');

function useMock() {
  const app = getApp();
  return !app || app.globalData.useMock;
}

function route(mockFn, realFn) {
  if (useMock()) return mockFn();
  return realFn();
}

const api = {
  // ===== 鉴权 =====
  login(code) { return route(() => mock.login(code), () => http.post('/api/auth/login', { code })); },
  bindPhone(data) { return route(() => mock.bindPhone(data), () => http.post('/api/auth/bindPhone', data)); },
  refreshToken() { return route(() => mock.login(), () => http.post('/api/auth/refresh', { refreshToken: store.getRefreshToken() }, false)); },

  // ===== 用户 =====
  getUserInfo() { return route(() => mock.getUserInfo(), () => http.get('/api/user/info')); },
  updateUserInfo(data) { return route(() => mock.updateUserInfo(data.nickname), () => http.put('/api/user/info', data)); },

  // ===== 类目 =====
  getCategoryTree() { return route(() => mock.getCategoryTree(), () => http.get('/api/category/tree', null, false)); },

  // ===== 商品 / 搜索 =====
  search(params) { return route(() => mock.search(params), () => http.get('/api/search', params, false)); },
  getItemDetail(id) { return route(() => mock.getItemDetail(id), () => http.get('/api/item/detail/' + id, null, false)); },
  publish(data) { return route(() => mock.publish(data), () => http.post('/api/item/publish', data)); },
  myItems() { return route(() => mock.myItems(), () => http.get('/api/item/mine')); },

  // ===== 图片上传 =====
  uploadImage(filePath) { return route(() => mock.uploadImage(filePath), () => http.upload(filePath, 'file')); },

  // ===== 收藏（真实后端统一用 /toggle 切换，返回 true=已收藏 / false=已取消）=====
  favoriteAdd(itemId) { return route(() => mock.favoriteAdd(itemId), () => http.post('/api/favorite/toggle', { itemId })); },
  favoriteRemove(itemId) { return route(() => mock.favoriteRemove(itemId), () => http.post('/api/favorite/toggle', { itemId })); },
  favoriteCheck(itemId) { return route(() => mock.favoriteCheck(itemId), () => http.get('/api/favorite/check?itemId=' + itemId, null, false)); },
  favoriteList(page, size) {
    return route(
      () => mock.favoriteList(page, size),
      () => http.get('/api/favorite/list?page=' + (page || 1) + '&size=' + (size || 20), null, false)
        .then((p) => ({ items: (p && p.records) || [], total: (p && p.total) || 0 }))
    );
  },

  // ===== 地址 =====
  getAddresses() { return route(() => mock.getAddresses(), () => http.get('/api/address/list')); },
  saveAddress(data) {
    const fn = data.id ? (d) => http.put('/api/address', d) : (d) => http.post('/api/address', d);
    return route(() => mock.saveAddress(data), () => fn(data));
  },
  deleteAddress(id) { return route(() => mock.deleteAddress(id), () => http.del('/api/address/' + id)); },

  // ===== 订单 / 支付 =====
  createOrder(data) { return route(() => mock.createOrder(data), () => http.post('/api/orders/create', data)); },
  prepay(data) { return route(() => mock.prepay(data), () => http.post('/api/pay/prepay', data)); },
  payNotify(data) { return route(() => mock.payNotify(data), () => http.post('/api/pay/notify', data, false)); },
  // Mock 支付完成（仅在 idlefish.pay.mock=true 的真实后端可用，复用 notify 幂等/验签逻辑）
  payMockComplete(payNo) { return route(() => mock.payMockComplete(payNo), () => http.post('/api/pay/mock/' + payNo, {}, false)); },
  listOrders(role) { return route(() => mock.listOrders(role), () => http.get('/api/orders?role=' + (role || ''))); },
  getOrder(orderNo) { return route(() => mock.getOrder(orderNo), () => http.get('/api/orders/' + orderNo)); },
  cancelOrder(orderNo) { return route(() => mock.cancelOrder(), () => http.post('/api/orders/' + orderNo + '/cancel')); },
  confirmOrder(orderNo) { return route(() => mock.confirmOrder(), () => http.post('/api/orders/' + orderNo + '/confirm')); },
  shipOrder(orderNo, data) { return route(() => mock.shipOrder(data), () => http.post('/api/orders/' + orderNo + '/ship', data)); },

  // ===== 退款 =====
  applyRefund(data) { return route(() => mock.applyRefund(data), () => http.post('/api/refunds/apply', data)); },
  listRefunds() { return route(() => mock.listRefunds(), () => Promise.resolve([])); },
  cancelRefund(refundNo) { return route(() => mock.cancelRefund(), () => http.post('/api/refund/' + refundNo + '/cancel')); },

  // ===== IM =====
  getConversations() { return route(() => mock.getConversations(), () => http.get('/api/im/conversations')); },
  getMessages(convId) { return route(() => mock.getMessages(convId), () => http.get('/api/im/messages?convId=' + convId)); },
  sendMessage(data) { return route(() => mock.sendMessage(data), () => http.post('/api/im/send', data)); },

  // ===== 埋点 =====
  track(event, extra) {
    const app = getApp();
    const payload = Object.assign({ event, ts: Date.now(), device_id: app.globalData.deviceId, page: '' }, extra || {});
    if (useMock()) return Promise.resolve({ ok: true });
    return http.post('/api/track', payload, false).catch(() => ({ ok: true }));
  },

  // ===== 后台（PC 端使用，小程序内仅示例调用）=====
  admin: {
    login(data) { return route(() => mock.adminLogin(data), () => http.post('/api/admin/login', data, false)); },
    stats() { return route(() => mock.adminStats(), () => http.get('/api/admin/stats')); },
    items() { return route(() => mock.adminItems(), () => http.get('/api/admin/items')); },
    audit(id, pass, reason) { return route(() => mock.adminAudit(), () => http.post('/api/admin/item/' + id + '/audit', { pass, reason })); },
    orders() { return route(() => mock.adminOrders(), () => http.get('/api/admin/orders')); },
    users() { return route(() => mock.adminUsers(), () => http.get('/api/admin/users')); },
    categories() { return route(() => mock.adminCategories(), () => http.get('/api/admin/categories')); },
    saveCategory(data) { return route(() => mock.adminSaveCategory(), () => http.post('/api/admin/category', data)); },
    risk() { return route(() => mock.adminRisk(), () => http.get('/api/admin/risk')); },
    auditLog() { return route(() => mock.adminAuditLog(), () => http.get('/api/admin/audit')); }
  }
};

module.exports = api;
