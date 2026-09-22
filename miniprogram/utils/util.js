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

module.exports = { formatPrice, formatTime, fromNow, statusText, ITEM_STATUS, ORDER_STATUS, REFUND_STATUS };
