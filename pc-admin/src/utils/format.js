// pc-admin/src/utils/format.js —— 通用格式化与状态映射
// 消除各视图中重复的「分→元」价格计算与「0/1 启用态」标签/文案样板。
// 通过 formatMixin 注入视图，保持选项式 API 兼容、免构建（不引入 element-plus）。

// 通用启用态映射：0 正常（success）/ 1 禁用（danger）
const ENABLE_STATUS = {
  0: { tag: 'success', text: '正常' },
  1: { tag: 'danger', text: '禁用' }
};

// 「分」转「元」并格式化为 ¥x.xx（null/undefined 视为 0）
export function yuan(fen) {
  return '¥' + ((fen == null ? 0 : fen) / 100).toFixed(2);
}

// 从 { value: { tag, text } } 映射取标签类型，缺省 info
export function tagType(map, val) {
  const e = map && map[val];
  return e ? e.tag : 'info';
}

// 从 { value: { tag, text } } 映射取文案，缺省原值/空串
export function tagText(map, val) {
  const e = map && map[val];
  if (e) return e.text;
  return val == null ? '' : String(val);
}

// 统一错误提示：优先透传服务端 e.msg，避免未捕获 Promise 拒绝导致静默失败。
// 仅展示、不抛出，调用方可安全用于 catch 兜底（保持弹窗/加载态由各自 finally 复位）。
export function notifyError(vm, e, fallback) {
  const msg = (e && e.msg) || fallback || '操作失败，请稍后重试';
  if (vm && typeof vm.$message !== 'undefined') vm.$message.error(msg);
}

// 启用态通用映射（与 sysuser 一致），供少量自定义视图直接引用
export const ENABLE_STATUS_MAP = ENABLE_STATUS;

// 注入式 mixin：为视图统一提供 yuan / tagText / tagType / statusTag / statusText 方法
export const formatMixin = {
  methods: {
    yuan,
    tagText,
    tagType,
    statusTag(v) { return tagType(ENABLE_STATUS, v); },
    statusText(v) { return tagText(ENABLE_STATUS, v); }
  }
};
