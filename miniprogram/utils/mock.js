// mock.js —— 本地 Mock 数据（useMock=true 时启用，无需后端即可演示全流程）
const IMG = (seed) => `https://picsum.photos/seed/${seed}/400/400`;

const categories = [
  { id: 1, name: '手机数码', children: [
    { id: 11, name: '手机', children: [
      { id: 111, name: 'iPhone' }, { id: 112, name: '安卓手机' } ] },
    { id: 12, name: '电脑/平板', children: [
      { id: 121, name: '笔记本' }, { id: 122, name: '平板电脑' } ] }
  ] },
  { id: 2, name: '服饰鞋包', children: [
    { id: 21, name: '女装' }, { id: 22, name: '男装' }, { id: 23, name: '鞋靴' } ] },
  { id: 3, name: '家居家电', children: [
    { id: 31, name: '大家电' }, { id: 32, name: '家具' } ] },
  { id: 4, name: '图书文娱', children: [
    { id: 41, name: '图书' }, { id: 42, name: '游戏' } ] },
  { id: 5, name: '母婴用品' },
  { id: 6, name: '运动户外' }
];

const sellerA = { id: 1001, nickname: '数码小哥', avatar: IMG('sellerA'), creditScore: 96 };
const sellerB = { id: 1002, nickname: '衣橱清仓', avatar: IMG('sellerB'), creditScore: 88 };

const items = [
  { id: 9001, title: 'Switch OLED 续航版 95新 配件齐全', price: 1380, originalPrice: 2099, img: IMG('switch'), images: [IMG('switch'), IMG('switch2'), IMG('switch3')], categoryId: 42, categoryName: '游戏', condition: '95新', location: '北京·朝阳', bargainEnable: true, seller: sellerA, status: 'onsale', stock: 1, viewCount: 326, favCount: 18, description: '自用几个月，屏幕无划痕，配件全含原装充电器与Joy-Con，支持面交验机。', createdAt: Date.now() - 3600 * 1000 * 5 },
  { id: 9002, title: 'iPhone 14 Pro 256G 暗紫色 国行', price: 5200, originalPrice: 8999, img: IMG('iphone'), images: [IMG('iphone'), IMG('iphone2')], categoryId: 111, categoryName: 'iPhone', condition: '99新', location: '上海·浦东', bargainEnable: false, seller: sellerA, status: 'onsale', stock: 1, viewCount: 1203, favCount: 64, description: '电池健康92%，无维修无进水，带原盒发票。', createdAt: Date.now() - 3600 * 1000 * 26 },
  { id: 9003, title: 'MacBook Air M1 13寸 8+256 轻微使用', price: 4200, originalPrice: 7999, img: IMG('mac'), images: [IMG('mac'), IMG('mac2')], categoryId: 121, categoryName: '笔记本', condition: '9成新', location: '广州·天河', bargainEnable: true, seller: sellerB, status: 'onsale', stock: 1, viewCount: 540, favCount: 30, description: '办公轻使用，电池循环120次，成色很好。', createdAt: Date.now() - 3600 * 1000 * 50 },
  { id: 9004, title: '九成新羽绒服 男款 L码 保暖', price: 199, originalPrice: 899, img: IMG('coat'), images: [IMG('coat')], categoryId: 22, categoryName: '男装', condition: '9成新', location: '杭州·西湖', bargainEnable: true, seller: sellerB, status: 'onsale', stock: 2, viewCount: 88, favCount: 5, description: '只穿过两次，干净无异味。', createdAt: Date.now() - 3600 * 1000 * 70 },
  { id: 9005, title: '索尼 WH-1000XM4 头戴降噪耳机', price: 1080, originalPrice: 2299, img: IMG('sony'), images: [IMG('sony')], categoryId: 11, categoryName: '手机', condition: '95新', location: '深圳·南山', bargainEnable: false, seller: sellerA, status: 'onsale', stock: 1, viewCount: 410, favCount: 22, description: '降噪神器，无磕碰，带收纳包。', createdAt: Date.now() - 3600 * 1000 * 90 },
  { id: 9006, title: '宜家 BILLY 书柜 白色 九成新', price: 120, originalPrice: 399, img: IMG('shelf'), images: [IMG('shelf')], categoryId: 32, categoryName: '家具', condition: '9成新', location: '成都·武侯', bargainEnable: true, seller: sellerB, status: 'onsale', stock: 1, viewCount: 60, favCount: 3, description: '自提优先，可小刀。', createdAt: Date.now() - 3600 * 1000 * 120 }
];

const me = { id: 2001, nickname: '我', avatar: IMG('me'), phone: '138****6027', creditScore: 92, status: 0 };

const addresses = [
  { id: 1, name: '家', receiver: '张三', phone: '13800006027', province: '北京市', city: '北京市', district: '朝阳区', detail: '幸福小区 8 号楼 2 单元 502', isDefault: true },
  { id: 2, name: '公司', receiver: '张三', phone: '13800006027', province: '北京市', city: '北京市', district: '海淀区', detail: '科技园 A 座 1203', isDefault: false }
];

const orders = [
  { orderNo: 'NO20260921001', item: items[0], amount: 1380, freight: 0, status: 'pending_pay', role: 'buyer', addressSnapshot: addresses[0], createdAt: Date.now() - 3600 * 1000, payNo: '', logistics: null },
  { orderNo: 'NO20260920002', item: items[2], amount: 4200, freight: 0, status: 'shipping', role: 'buyer', addressSnapshot: addresses[0], createdAt: Date.now() - 3600 * 1000 * 30, payNo: 'P20260920', logistics: { company: '顺丰速运', logisticNo: 'SF1234567890' } },
  { orderNo: 'NO20260919003', item: items[1], amount: 5200, freight: 0, status: 'completed', role: 'seller', addressSnapshot: addresses[1], createdAt: Date.now() - 3600 * 1000 * 60, payNo: 'P20260919', logistics: { company: '顺丰速运', logisticNo: 'SF0987654321' } }
];

const conversations = [
  { convId: 'C1', peer: { id: 1001, nickname: '数码小哥', avatar: IMG('sellerA') }, item: items[0], lastMsg: '这款支持面交验机哦~', unread: 2, updatedAt: Date.now() - 60000 },
  { convId: 'C2', peer: { id: 1002, nickname: '衣橱清仓', avatar: IMG('sellerB') }, item: items[3], lastMsg: '可以小刀，诚心要给你包邮', unread: 0, updatedAt: Date.now() - 3600 * 1000 * 3 }
];

const messages = {
  C1: [
    { msgId: 'm1', convId: 'C1', senderId: 1001, type: 'text', content: '你好，Switch还在的', seq: 1, createdAt: Date.now() - 120000 },
    { msgId: 'm2', convId: 'C1', senderId: 2001, type: 'text', content: '成色怎么样？配件全吗', seq: 2, createdAt: Date.now() - 90000 },
    { msgId: 'm3', convId: 'C1', senderId: 1001, type: 'text', content: '这款支持面交验机哦~', seq: 3, createdAt: Date.now() - 60000 }
  ],
  C2: [
    { msgId: 'm4', convId: 'C2', senderId: 1002, type: 'text', content: '可以小刀，诚心要给你包邮', seq: 1, createdAt: Date.now() - 3600 * 1000 * 3 }
  ]
};

const riskEvents = [
  { id: 'R1', userId: 1001, ruleId: 'R_NEW_DEVICE', score: 35, action: '放行', createdAt: Date.now() - 3600 * 1000 * 2 },
  { id: 'R2', userId: 1003, ruleId: 'R_FREQ_PUBLISH', score: 72, action: '人工复核', createdAt: Date.now() - 3600 * 1000 * 5 }
];

// 本地收藏集合（mock 模式使用）
const favSet = new Set();

function delay(data, ms) {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms || 200));
}

module.exports = {
  IMG, categories, items, me, addresses, orders, conversations, messages, riskEvents, sellerA, sellerB,
  delay,
  // —— 以下为可直接调用的 mock 业务方法 ——
  login() { return delay({ token: 'mock-token-123', refreshToken: 'mock-refresh-123', user: me }); },
  bindPhone() { return delay({ ok: true }); },
  getUserInfo() { return delay(me); },
  updateUserInfo(nickname) { me.nickname = nickname; return delay(me); },
  getCategoryTree() { return delay(categories); },
  search({ keyword, categoryId, sort, page, size }) {
    let list = items.slice();
    if (keyword) list = list.filter(i => i.title.indexOf(keyword) >= 0);
    if (categoryId) {
      // 顶级类目（含子类）按浏览返回全部；叶子类目精确匹配
      const isTop = categories.some(c => c.id == categoryId && c.children && c.children.length);
      if (!isTop) list = list.filter(i => String(i.categoryId) === String(categoryId));
    }
    if (sort === 'price_asc') list.sort((a, b) => a.price - b.price);
    if (sort === 'price_desc') list.sort((a, b) => b.price - a.price);
    const total = list.length;
    const start = (page - 1) * size;
    return delay({ items: list.slice(start, start + size), total, suggest: keyword ? null : '热门' });
  },
  getItemDetail(id) {
    const it = items.find(i => i.id == id) || items[0];
    return delay(Object.assign({}, it, { images: it.images || [it.img] }));
  },
  publish(dto) {
    const id = 9000 + Math.floor(Math.random() * 999);
    return delay({ id, status: 'pending_review' });
  },
  myItems() { return delay(items.filter(i => i.seller.id === me.id).concat([{ id: 9999, title: '我的闲置（已发布示例）', price: 88, img: IMG('my'), status: 'onsale', categoryName: '其他' }])); },
  getAddresses() { return delay(addresses); },
  saveAddress(dto) { const a = Object.assign({ id: Date.now() }, dto); addresses.push(a); return delay(a); },
  deleteAddress(id) { return delay({ ok: true }); },
  createOrder(dto) { return delay({ orderNo: 'NO' + Date.now(), amount: dto.amount || 100, status: 'pending_pay', lockExpireAt: Date.now() + 1800000 }); },
  prepay(dto) { return delay({ payNo: 'P' + Date.now(), prepayParams: { timeStamp: '1', nonceStr: 'x', package: 'prepay_id=mock', signType: 'MD5', paySign: 'mock' } }); },
  payNotify(dto) { return delay({ ok: true }); },
  payMockComplete(payNo) { return delay({ ok: true }); },
  listOrders(role) { return delay(orders.filter(o => !role || o.role === role)); },
  getOrder(orderNo) { return delay(orders.find(o => o.orderNo === orderNo) || orders[0]); },
  cancelOrder() { return delay({ ok: true }); },
  confirmOrder() { return delay({ ok: true }); },
  shipOrder(dto) { return delay({ ok: true }); },
  applyRefund(dto) { return delay({ refundNo: 'RF' + Date.now(), status: 'apply' }); },
  listRefunds() { return delay([]); },
  cancelRefund() { return delay({ ok: true }); },
  // ===== 图片上传（mock 返回占位图地址）=====
  uploadImage(filePath) { return delay('https://picsum.photos/seed/' + Date.now() + '/800/800'); },
  // ===== 收藏（mock 本地集合）=====
  favoriteAdd(itemId) { favSet.add(Number(itemId)); return delay({ ok: true }); },
  favoriteRemove(itemId) { favSet.delete(Number(itemId)); return delay({ ok: true }); },
  favoriteCheck(itemId) { return delay({ favorited: favSet.has(Number(itemId)) }); },
  favoriteList(page, size) {
    const ids = Array.from(favSet);
    const total = ids.length;
    const start = (page - 1) * size;
    const pageIds = ids.slice(start, start + size);
    const list = items.filter(i => pageIds.indexOf(i.id) >= 0);
    return delay({ items: list, total, page: page || 1, size: size || 20 });
  },
  getConversations() { return delay(conversations); },
  getMessages(convId) { return delay(messages[convId] || []); },
  sendMessage(msg) {
    const seq = (messages[msg.convId] || []).length + 1;
    const m = Object.assign({ msgId: 'm' + Date.now(), seq, createdAt: Date.now() }, msg);
    (messages[msg.convId] = messages[msg.convId] || []).push(m);
    return delay(m);
  },
  track() { return delay({ ok: true }); },
  // —— 后台 mock ——
  adminLogin() { return delay({ token: 'admin-mock-token', user: { username: 'admin', role: 'admin' } }); },
  adminStats() { return delay({ gmv: 128600, orderCnt: 342, userCnt: 1560, itemCnt: 892, pendingReview: 23, refunding: 5, todayRegister: 42 }); },
  adminItems() { return delay(items.map(i => Object.assign({}, i, { auditInfo: { status: i.status === 'onsale' ? 'passed' : 'pending' } }))); },
  adminAudit() { return delay({ ok: true }); },
  adminOrders() { return delay(orders); },
  adminUsers() { return delay([me, sellerA, sellerB, { id: 1003, nickname: '新注册用户', status: 0 }]); },
  adminCategories() { return delay(categories); },
  adminSaveCategory() { return delay({ ok: true }); },
  adminRisk() { return delay(riskEvents); },
  adminAuditLog() { return delay([{ opId: 'A1', operator: 'admin', target: 'item:9001', action: '通过审核', ts: Date.now() - 3600 * 1000 }]); }
};
