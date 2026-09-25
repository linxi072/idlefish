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
  recommend(params) { return route(() => mock.search(params), () => http.get('/api/search/recommend', params, false)); },
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

  // ===== 退款 / 售后（后端 base: /api/refunds —— 注意是复数 refunds）=====
  applyRefund(data) { return route(() => mock.applyRefund(data), () => http.post('/api/refunds/apply', data)); },
  getRefund(refundNo) { return route(() => mock.getRefund(refundNo), () => http.get('/api/refunds/' + refundNo)); },
  // 某订单的退款单列表（后端无「我的退款」全局列表，故按订单维度查询）
  listRefundsByOrder(orderNo) { return route(() => mock.listRefundsByOrder(orderNo), () => http.get('/api/refunds/order/' + orderNo)); },
  agreeRefund(refundNo) { return route(() => mock.agreeRefund(refundNo), () => http.post('/api/refunds/' + refundNo + '/agree')); },
  rejectRefund(refundNo, reason) { return route(() => mock.rejectRefund(refundNo, reason), () => http.post('/api/refunds/' + refundNo + '/reject?reason=' + encodeURIComponent(reason || ''))); },
  returnRefundLogistics(refundNo, logisticsNo) { return route(() => mock.returnRefundLogistics(refundNo, logisticsNo), () => http.post('/api/refunds/' + refundNo + '/return-logistics?logisticsNo=' + encodeURIComponent(logisticsNo || ''))); },
  confirmRefundReturn(refundNo) { return route(() => mock.confirmRefundReturn(refundNo), () => http.post('/api/refunds/' + refundNo + '/confirm-return')); },
  platformRefund(refundNo) { return route(() => mock.platformRefund(refundNo), () => http.post('/api/refunds/platform/' + refundNo)); },
  cancelRefund(refundNo) { return route(() => mock.cancelRefund(refundNo), () => http.post('/api/refunds/' + refundNo + '/cancel')); },

  // ===== 评价 / 信用（后端 base: /api/reviews，对齐 ReviewController / ReviewSubmitDTO）=====
  // 提交评价：orderNo + rating(1-5,必填) + content(≤512,选填) + anonymous(0/1)
  submitReview(data) { return route(() => mock.submitReview(data), () => http.post('/api/reviews/submit', data)); },
  // 某商品评价列表（展示，仅通过）
  listReviewsByItem(itemId) { return route(() => mock.listReviewsByItem(itemId), () => http.get('/api/reviews/item/' + itemId, null, false)); },
  // 我发出的评价（仅通过）
  myReviews() { return route(() => mock.myReviews(), () => http.get('/api/reviews/my')); },
  // 我收到的评价（被评价方视角，仅通过）
  receivedReviews() { return route(() => mock.receivedReviews(), () => http.get('/api/reviews/received')); },

  // ===== 钱包 / 提现（后端 base: /api/wallet，对齐 WalletController / WithdrawalService，金额单位均为「分」）=====
  // 余额概览：{ withdrawable, frozen, settledTotal }
  walletBalance() { return route(() => mock.walletBalance(), () => http.get('/api/wallet/balance')); },
  // 钱包流水（仅钱包相关类型 SETTLE/FREEZE/WITHDRAW/UNFREEZE）：归一化为 { items, total }
  walletFlows(page, size) {
    return route(
      () => mock.walletFlows(page, size),
      () => http.get('/api/wallet/flows?page=' + (page || 1) + '&size=' + (size || 20))
        .then((p) => ({ items: (p && p.records) || [], total: (p && p.total) || 0 }))
    );
  },
  // 提现申请：amount 为「分」，account 为收款账号；后端 WithdrawalService.apply 先冻结（FREEZE）
  walletWithdraw(amountFen, account) {
    return route(
      () => mock.walletWithdraw(amountFen, account),
      () => http.post('/api/wallet/withdraw?amount=' + amountFen + '&account=' + encodeURIComponent(account || ''))
    );
  },

  // ===== IM（对齐 ImController / ConversationVO / MessageVO）=====
  // 真实模式：后端 ConversationVO 为扁平结构（peerId/peerName/itemId/lastMessage/...），
  // 映射成 message.js 期望的嵌套 peer/item 形态，保持前后端契约一致
  getConversations() {
    return route(
      () => mock.getConversations(),
      () => http.get('/api/im/conversations').then((list) => (list || []).map((vo) => ({
        convId: vo.convId,
        peer: { id: vo.peerId, nickname: vo.peerName || '', avatar: '' },
        item: { id: vo.itemId || '', title: '', img: '', price: 0 },
        lastMsg: vo.lastMessage || '',
        unread: vo.unread || 0,
        updatedAt: vo.lastTime || ''
      })))
    );
  },
  getMessages(convId) {
    return route(
      () => mock.getMessages(convId),
      // 后端 MessageVO 无 msgId，以 seq 兜底（chat.wxml 用 wx:key="msgId"）
      () => http.get('/api/im/messages?convId=' + convId)
        .then((list) => (list || []).map((m) => Object.assign({}, m, { msgId: 's' + m.seq })))
    );
  },
  // 发送消息：后端 /api/im/send 用 @RequestParam（receiverId/itemId/content/type），故真实模式走 query
  sendMessage(data) {
    return route(
      () => mock.sendMessage(data),
      () => {
        const q = 'receiverId=' + data.receiverId + '&itemId=' + (data.itemId || 0) +
          '&content=' + encodeURIComponent(data.content || '') + '&type=' + (data.type || 'text');
        return http.post('/api/im/send?' + q, null, true);
      }
    );
  },

  // ===== 议价（对齐 BargainController / BargainCreateDTO，金额单位：分）=====
  createBargain(data) { return route(() => mock.createBargain(data), () => http.post('/api/bargain/create', data)); },
  acceptBargain(bargainId) { return route(() => mock.acceptBargain(bargainId), () => http.post('/api/bargain/accept?bargainId=' + bargainId)); },
  listBargains(convId) { return route(() => mock.listBargains(convId), () => http.get('/api/bargain/list?convId=' + convId)); },

  // ===== 站内通知（对齐 NotificationController / NotificationVO，IPage → {records,total}）=====
  notifyList(page, size) {
    return route(
      () => mock.notifyList(page, size),
      () => http.get('/api/notify/list?page=' + (page || 1) + '&size=' + (size || 20))
        .then((p) => ({ records: (p && p.records) || [], total: (p && p.total) || 0 }))
    );
  },
  notifyUnread() { return route(() => mock.notifyUnread(), () => http.get('/api/notify/unread-count')); },
  notifyRead(id) { return route(() => mock.notifyRead(id), () => http.post('/api/notify/read', { id })); },
  notifyReadAll() { return route(() => mock.notifyReadAll(), () => http.post('/api/notify/read-all')); },

  // ===== 优惠券（对齐 CouponController / CouponService：center/claim/my/available，金额单位：分）=====
  // 领券中心（分页，仅可领）：IPage → {records,total}
  couponCenter(page, size) {
    return route(
      () => mock.couponCenter(page, size),
      () => http.get('/api/coupon/center?page=' + (page || 1) + '&size=' + (size || 20))
        .then((p) => ({ records: (p && p.records) || [], total: (p && p.total) || 0 }))
    );
  },
  // 领取优惠券：couponId 走 query
  couponClaim(couponId) { return route(() => mock.couponClaim(couponId), () => http.post('/api/coupon/claim?couponId=' + couponId)); },
  // 我的优惠券（status 可选：UNUSED/USED/EXPIRED，空=全部）：返回数组
  couponMy(status) { return route(() => mock.couponMy(status), () => http.get('/api/coupon/my' + (status ? ('?status=' + status) : ''))); },
  // 下单可用券（itemId + 商品金额（分））：返回数组（含 discountAmount 可抵扣）
  couponAvailable(itemId, amount) { return route(() => mock.couponAvailable(itemId, amount), () => http.get('/api/coupon/available?itemId=' + itemId + '&amount=' + amount)); },

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
