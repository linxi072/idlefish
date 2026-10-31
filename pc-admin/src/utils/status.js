// pc-admin/src/utils/status.js —— 统一状态字典（ITER-U04）
// 供 <StatusTag :value="..." :map="..." /> 复用，避免各页面重复定义映射。
// 字典项格式：{ text, type }，type 取值与 Element Plus el-tag 一致。

// 启用 / 禁用（用户、角色、菜单、字典等通用）
export const ENABLE_STATUS = {
  0: { text: '正常', type: 'success' },
  1: { text: '已封禁', type: 'danger' }
};

// 通用启用态（兼容「禁用」语义，如角色/菜单）
export const ACTIVE_STATUS = {
  0: { text: '启用', type: 'success' },
  1: { text: '停用', type: 'info' }
};

// 订单状态（与后端 Order.status 枚举一致）
export const ORDER_STATUS = {
  pending_pay: { text: '待支付', type: 'info' },
  paid: { text: '已支付', type: 'warning' },
  pending_ship: { text: '待发货', type: 'warning' },
  shipping: { text: '待收货', type: 'primary' },
  completed: { text: '已完成', type: 'success' },
  closed: { text: '已关闭', type: 'info' },
  refunding: { text: '退款中', type: 'danger' }
};

// 商品审核状态（列表可能返回 pending / pending_review 两种口径）
export const AUDIT_STATUS = {
  pending: { text: '待审核', type: 'warning' },
  pending_review: { text: '待审核', type: 'warning' },
  passed: { text: '已通过', type: 'success' },
  onsale: { text: '在售', type: 'success' },
  rejected: { text: '已驳回', type: 'danger' }
};

// 维权工单状态
export const DISPUTE_STATUS = {
  PENDING: { text: '待卖家举证', type: 'warning' },
  SELLER_REPLIED: { text: '卖家已举证', type: 'primary' },
  PLATFORM: { text: '平台介入中', type: 'primary' },
  RESOLVED: { text: '已裁决', type: 'success' },
  CLOSED: { text: '已关闭', type: 'info' },
  CANCELED: { text: '已撤销', type: 'info' }
};

// 工单状态（客服）
export const WORK_ORDER_STATUS = {
  OPEN: { text: '待处理', type: 'warning' },
  IN_PROGRESS: { text: '处理中', type: 'primary' },
  WAITING_USER: { text: '待用户回复', type: 'info' },
  CLOSED: { text: '已关闭', type: 'info' }
};

// ===== ITER-F05 同城回收与本地履约 =====
// 回收单状态（与 RecycleStateMachine 一致）
export const RECYCLE_STATUS = {
  PENDING_PICKUP: { text: '待上门取件', type: 'warning' },
  PICKED_UP: { text: '已揽收', type: 'primary' },
  INSPECTING: { text: '质检中', type: 'primary' },
  QUOTED: { text: '待确认报价', type: 'warning' },
  CONFIRMED: { text: '已确认待放款', type: 'primary' },
  PAID: { text: '已放款', type: 'success' },
  CANCELED: { text: '已取消', type: 'info' },
  REJECTED: { text: '已拒收退回', type: 'info' },
  UNAVAILABLE: { text: '服务商不可用', type: 'danger' }
};

// 同城配送单状态（与 LocalDeliveryStateMachine 一致）
export const DELIVERY_STATUS = {
  CREATED: { text: '已下单', type: 'info' },
  ACCEPTED: { text: '骑手已接单', type: 'primary' },
  DELIVERING: { text: '配送中', type: 'warning' },
  ARRIVED: { text: '已送达', type: 'primary' },
  SIGNED: { text: '已签收', type: 'success' },
  CANCELED: { text: '已取消', type: 'info' },
  FAILED: { text: '配送失败', type: 'danger' }
};

// 回收取件方式
export const RECYCLE_PICKUP_TYPE = {
  HOME: { text: '上门取件', type: 'primary' },
  SELF: { text: '到店自送', type: 'info' }
};

// 同城配送业务类型
export const DELIVERY_BIZ_TYPE = {
  ORDER: { text: '订单配送', type: 'primary' },
  RECYCLE: { text: '回收取件', type: 'warning' }
};

// 成色档位（回收估价维度）
export const CONDITION_LEVEL = {
  1: { text: '全新' },
  2: { text: '95新' },
  3: { text: '9成新' },
  4: { text: '8成新' },
  5: { text: '功能完好' }
};

// 从字典安全取文案 / 类型（用于非组件场景，如导出或图表 label）
export function statusText(map, value, fallback = '') {
  const e = map && value != null ? map[value] : null;
  if (e == null) return value == null ? fallback : String(value);
  return typeof e === 'object' ? (e.text || fallback) : String(e);
}

export function statusType(map, value, fallback = 'info') {
  const e = map && value != null ? map[value] : null;
  if (e == null || typeof e !== 'object') return fallback;
  return e.type || e.tag || fallback;
}
