// pc-admin/src/utils/filterTemplates.js
// ITER-U03 REQ-06 筛选模板（本地持久化，零后端依赖、零网络）
// 按页面维度隔离（pageKey），支持命名保存、默认模板、删除、应用、导入/导出。
// 设计为纯函数 + 可选存储注入：localStorage 可用时落盘，否则退化为内存 shim（保证 node 单测可跑）。

const STORAGE_KEY = 'idlefish.filterTemplates.v1';

// 解析可用的存储（localStorage 优先，隐私模式/缺失时退化为内存 Map shim）
function getStore() {
  try {
    if (typeof globalThis !== 'undefined' && globalThis.localStorage) {
      const k = '__ft_probe__';
      globalThis.localStorage.setItem(k, '1');
      globalThis.localStorage.removeItem(k);
      return globalThis.localStorage;
    }
  } catch (e) {
    /* fallthrough to in-memory shim */
  }
  if (!getStore._mem) {
    const m = new Map();
    getStore._mem = {
      getItem: (k) => (m.has(k) ? m.get(k) : null),
      setItem: (k, v) => m.set(k, String(v)),
      removeItem: (k) => m.delete(k)
    };
  }
  return getStore._mem;
}

function readAll() {
  try {
    const raw = getStore().getItem(STORAGE_KEY);
    if (!raw) return {};
    const obj = JSON.parse(raw);
    return obj && typeof obj === 'object' ? obj : {};
  } catch (e) {
    return {};
  }
}

function writeAll(obj) {
  try {
    getStore().setItem(STORAGE_KEY, JSON.stringify(obj || {}));
    return true;
  } catch (e) {
    return false;
  }
}

function genId() {
  return 'tpl_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8);
}

function normalizeFilters(filters) {
  if (!filters || typeof filters !== 'object') return {};
  const out = {};
  for (const k of Object.keys(filters)) {
    const v = filters[k];
    // 去除未设置 / 空值（undefined、null、空字符串）；保留 0 / false 等具语义的值
    if (v === undefined || v === null || v === '') continue;
    out[k] = v;
  }
  return out;
}

function sortTemplates(list) {
  return [...list].sort((a, b) => {
    if (!!a.isDefault !== !!b.isDefault) return a.isDefault ? -1 : 1;
    return (b.createdAt || 0) - (a.createdAt || 0);
  });
}

/** 列出某页面的全部模板（默认置顶，其余按创建时间倒序）。 */
export function list(pageKey) {
  if (!pageKey) return [];
  const all = readAll();
  const arr = Array.isArray(all[pageKey]) ? all[pageKey] : [];
  return sortTemplates(arr);
}

/**
 * 保存/覆盖模板。同名视为覆盖（保留原 id/时间戳）。
 * @param {string} pageKey 页面标识
 * @param {string} name 模板名称（非空）
 * @param {object} filters 当前筛选条件快照（会被归一化，去除空值）
 * @param {{isDefault?: boolean}} [options] 是否设为默认
 * @returns {object} 保存后的模板
 */
export function save(pageKey, name, filters, options) {
  if (!pageKey) throw new Error('pageKey 必填');
  name = (name || '').trim();
  if (!name) throw new Error('模板名称不能为空');
  const isDefault = !!(options && options.isDefault);
  const all = readAll();
  const arr = Array.isArray(all[pageKey]) ? all[pageKey] : [];
  const idx = arr.findIndex((t) => t.name === name);
  let tpl;
  if (idx >= 0) {
    tpl = { ...arr[idx], filters: normalizeFilters(filters), isDefault, updatedAt: Date.now() };
  } else {
    tpl = { id: genId(), name, filters: normalizeFilters(filters), isDefault, createdAt: Date.now() };
  }
  if (isDefault) arr.forEach((t) => { if (t !== tpl) t.isDefault = false; });
  if (idx >= 0) arr[idx] = tpl; else arr.push(tpl);
  all[pageKey] = arr;
  writeAll(all);
  return tpl;
}

/** 删除模板，返回是否实际删除。 */
export function remove(pageKey, id) {
  if (!pageKey || !id) return false;
  const all = readAll();
  const arr = Array.isArray(all[pageKey]) ? all[pageKey] : [];
  const next = arr.filter((t) => t.id !== id);
  all[pageKey] = next;
  writeAll(all);
  return next.length !== arr.length;
}

/** 设置/取消默认（置为默认时会清除同页其它默认）。 */
export function setDefault(pageKey, id, isDefault) {
  if (!pageKey || !id) return false;
  const flag = isDefault !== false;
  const all = readAll();
  const arr = Array.isArray(all[pageKey]) ? all[pageKey] : [];
  let changed = false;
  arr.forEach((t) => {
    if (t.id === id) { t.isDefault = flag; changed = true; }
    else if (flag) t.isDefault = false;
  });
  writeAll(all);
  return changed;
}

/** 取某页面的默认模板（无则 null）。 */
export function getDefault(pageKey) {
  const arr = list(pageKey);
  return arr.find((t) => t.isDefault) || null;
}

/** 应用模板：返回其筛选条件快照的深拷贝（不存在返回 null）。 */
export function apply(pageKey, id) {
  const all = readAll();
  const arr = Array.isArray(all[pageKey]) ? all[pageKey] : [];
  const tpl = arr.find((t) => t.id === id);
  return tpl ? { ...normalizeFilters(tpl.filters) } : null;
}

/** 导出全部模板为 JSON 字符串。 */
export function exportAll() {
  return JSON.stringify(readAll(), null, 2);
}

/**
 * 导入模板。
 * @param {string} jsonStr JSON 字符串（与 exportAll 同结构）
 * @param {{merge?: boolean}} [options] merge=false 则整体替换
 * @returns {number} 导入模板条数
 */
export function importAll(jsonStr, options) {
  if (!jsonStr) throw new Error('导入内容为空');
  let obj;
  try {
    obj = JSON.parse(jsonStr);
  } catch (e) {
    throw new Error('JSON 解析失败：' + e.message);
  }
  if (!obj || typeof obj !== 'object') throw new Error('导入格式不合法');
  const merge = !options || options.merge !== false;
  const all = merge ? readAll() : {};
  let count = 0;
  for (const key of Object.keys(obj)) {
    const arr = Array.isArray(obj[key]) ? obj[key] : [];
    const existing = Array.isArray(all[key]) ? all[key] : [];
    const byName = new Map(existing.map((t) => [t.name, t]));
    for (const t of arr) {
      if (!t || typeof t !== 'object' || !t.name) continue;
      const filters = normalizeFilters(t.filters);
      if (byName.has(t.name)) {
        const cur = byName.get(t.name);
        cur.filters = filters;
        if (t.isDefault) {
          existing.forEach((x) => { if (x !== cur) x.isDefault = false; });
          cur.isDefault = true;
        }
      } else {
        const tpl = {
          id: t.id || genId(), name: t.name, filters, isDefault: !!t.isDefault,
          createdAt: t.createdAt || Date.now(), updatedAt: t.updatedAt || Date.now()
        };
        existing.push(tpl);
        byName.set(t.name, tpl);
      }
      count++;
    }
    all[key] = existing;
  }
  writeAll(all);
  return count;
}

/** 测试/清理辅助：清空全部模板。 */
export function _clearAll() {
  writeAll({});
}
