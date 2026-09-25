// util.js —— 通用工具
// 金额单位契约：后端所有金额字段统一为「分」，前端展示统一 /100 转为「元」并保留两位
function formatPrice(fen) {
  if (fen === null || fen === undefined || fen === '') return '0.00';
  const v = Number(fen) / 100;
  if (isNaN(v)) return '0.00';
  return v.toFixed(2);
}

function formatTime(ts) {
  if (!ts) return '';
  const d = (ts instanceof Date) ? ts : new Date(ts);
  const p = (x) => (x < 10 ? '0' + x : '' + x);
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function fromNow(ts) {
  if (!ts) return '';
  const diff = Date.now() - new Date(ts).getTime();
  const m = 60000, h = 3600000, d = 86400000;
  if (diff < m) return '刚刚';
  if (diff < h) return Math.floor(diff / m) + '分钟前';
  if (diff < d) return Math.floor(diff / h) + '小时前';
  if (diff < 30 * d) return Math.floor(diff / d) + '天前';
  return formatTime(ts).slice(0, 10);
}

// 商品状态中文
const ITEM_STATUS = {
  draft: '草稿', pending_review: '审核中', onsale: '在售',
  locked: '交易中', sold: '已售出', rejected: '已驳回', off_shelf: '已下架'
};
// 订单状态中文
const ORDER_STATUS = {
  pending_pay: '待支付', paid: '已支付', pending_ship: '待发货',
  shipping: '待收货', completed: '已完成', closed: '已关闭'
};
// 退款状态中文
const REFUND_STATUS = {
  apply: '退款申请中', wait_seller: '待卖家处理', platform: '平台介入中',
  refunding: '退款处理中', refunded: '已退款', rejected: '已拒绝', canceled: '已撤销'
};

function statusText(type, code) {
  if (type === 'item') return ITEM_STATUS[code] || code;
  if (type === 'order') return ORDER_STATUS[code] || code;
  if (type === 'refund') return REFUND_STATUS[code] || code;
  return code;
}

// ===== 退款/售后辅助（F-09）=====
// 退款类型中文
const REFUND_TYPE = { only_refund: '仅退款', return_refund: '退货退款' };

// 退款终态：refunded/refunded/rejected/canceled，终态不可再撤销或平台介入
const REFUND_TERMINAL = { refunded: true, rejected: true, canceled: true };
function isRefundTerminal(code) { return !!REFUND_TERMINAL[code]; }

// 退款进度步骤下标（详情页时间线用）：0=卖家处理 1=退款中 2=退款成功；-1=非正常推进（已拒绝/已撤销）
function refundStepIndex(code) {
  if (code === 'apply' || code === 'wait_seller') return 0;
  if (code === 'platform' || code === 'refunding') return 1;
  if (code === 'refunded') return 2;
  return -1;
}

// 允许申请退款的订单状态（严格对齐后端 RefundService.apply 守卫：仅 paid / shipping）
function canApplyRefund(orderStatus) {
  return orderStatus === 'paid' || orderStatus === 'shipping';
}

// 元 → 分（金额单位契约：后端统一「分」）。非法输入返回 -1
function yuanToFen(yuan) {
  const v = Number(yuan);
  if (yuan === '' || yuan === null || yuan === undefined || isNaN(v)) return -1;
  return Math.round(v * 100);
}

// ===== 评价 / 信用（F-09 evaluate，对齐后端 ReviewRole / ReviewSubmitDTO）=====
// 评价角色中文（后端 ReviewRole：BUYER_SELLER=买家评价卖家，SELLER_BUYER=卖家评价买家）
const REVIEW_ROLE = { BUYER_SELLER: '我评价卖家', SELLER_BUYER: '我评价买家' };
function reviewRoleLabel(code) { return REVIEW_ROLE[code] || code; }

// 订单是否允许评价（严格对齐后端 ReviewService.submit 守卫：仅 COMPLETED / CLOSED）
function canEvaluate(orderStatus) {
  return orderStatus === 'completed' || orderStatus === 'closed';
}

// 订单角色 → 评价角色 code（买家评价卖家 / 卖家评价买家）
function orderRoleToReviewRole(orderRole) {
  return orderRole === 'buyer' ? 'BUYER_SELLER' : 'SELLER_BUYER';
}

// 评分转 5 格布尔数组，便于 WXML 用 wx:for 渲染星标（filled=实心）
function ratingArray(rating) {
  const n = Math.max(0, Math.min(5, Number(rating) || 0));
  const arr = [];
  for (let i = 1; i <= 5; i++) arr.push(i <= n);
  return arr;
}

// ===== 站内通知（F-09 notification，对齐后端 NotificationType）=====
// 通知类型中文
const NOTIFY_TYPE = {
  order_paid: '订单支付', order_shipped: '卖家发货', order_confirmed: '确认收货', order_closed: '订单关闭',
  refund_apply: '退款申请', refund_success: '退款成功', refund_rejected: '退款被拒',
  refund_canceled: '退款撤销', refund_platform: '平台介入', item_approved: '商品过审',
  item_rejected: '商品驳回', remind_ship: '发货提醒', settlement_success: '结算到账',
  withdraw_apply: '提现申请', withdraw_approve: '提现通过', withdraw_reject: '提现驳回'
};
function notifyTypeText(code) { return NOTIFY_TYPE[code] || '通知'; }

// 通知图标（按类型归类）
function notifyIcon(type) {
  if (type && type.indexOf('refund') >= 0) return '💸';
  if (type && type.indexOf('withdraw') >= 0) return '🏦';
  if (type && type.indexOf('order') >= 0) return '📦';
  if (type && type.indexOf('item') >= 0) return '✅';
  if (type === 'settlement_success') return '💰';
  if (type === 'remind_ship') return '🚚';
  return '🔔';
}

module.exports = {
  formatPrice, formatTime, fromNow, statusText,
  ITEM_STATUS, ORDER_STATUS, REFUND_STATUS,
  REFUND_TYPE, isRefundTerminal, refundStepIndex, canApplyRefund, yuanToFen,
  REVIEW_ROLE, reviewRoleLabel, canEvaluate, orderRoleToReviewRole, ratingArray,
  NOTIFY_TYPE, notifyTypeText, notifyIcon
};
