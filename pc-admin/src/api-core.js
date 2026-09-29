// pc-admin/src/api-core.js —— 接口层公共内核（单一来源，避免与按域拆分模块形成循环依赖）
// 承载：USE_MOCK 开关、BASE、令牌工具、统一 axios 实例与响应拦截、req()、pageTo()。
//
// 轻量类型约束（#5）：ESM 前端不引入 TS 构建步骤，故以 JSDoc 表达契约形状，作为前端字段对照。
/**
 * 后端统一响应信封。
 * @typedef {Object} Result
 * @property {number} code 业务码（0=成功，非 0=失败，20001/20002 触发登出）
 * @property {string} [msg] 提示信息
 * @property {*} [data] 业务数据负载
 */
/**
 * 分页归一结构：后端 IPage（{records,total}）经 {@link pageTo} 转换后的前端结构。
 * @typedef {Object} PageData
 * @property {Array<*>} list 当前页记录
 * @property {number} total 总记录数
 */
import axios from 'axios';

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

/**
 * 统一请求：返回后端 Result.data。GET 用 params，非 GET 用 data（缺省回退 params）。
 * @param {string} method HTTP 方法
 * @param {string} path 相对路径
 * @param {*} [data] 非 GET 请求体
 * @param {Object} [params] 查询参数
 * @returns {Promise<*>} Result.data
 */
async function req(method, path, data, params) {
  const cfg = { method, url: path, headers: {} };
  const tk = getToken();
  if (tk) cfg.headers['Authorization'] = 'Bearer ' + tk;
  if (method === 'GET') cfg.params = params || {};
  else {
    // 非 GET：第 3 参 data 优先，缺省时回退第 4 参 params（兼容 Login/驳回/发货/封禁/保存类目等把 body 传在 params 的调用）
    cfg.data = data != null ? data : (params || {});
    cfg.headers['Content-Type'] = 'application/json';
  }
  const resp = await http.request(cfg);
  return resp.data.data;
}

// IPage 归一化为 { list, total }（R-16：后端分页返回 records/total）
/**
 * 将后端 IPage（{records,total}）归一为前端 PageData（{list,total}）。
 * @param {Object} [p] 后端分页对象
 * @returns {PageData}
 */
function pageTo(p) {
  if (!p) return { list: [], total: 0 };
  return { list: p.records || [], total: p.total || 0 };
}

export { req, pageTo };
