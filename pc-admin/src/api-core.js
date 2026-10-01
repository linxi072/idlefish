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
// 免构建 CDN 架构：axios 由 index.html 的 UMD <script> 注入为全局 window.axios（同 Vue/ElementPlus/echarts），
// 不得使用裸 import 说明符（浏览器无 import map 会抛 "Failed to resolve module specifier 'axios'" 导致整页白屏）。
const axios = window.axios;

// 联调开关（R-24）：USE_MOCK 不再硬编码，改为外部可控，避免每次联调都改 JS 源码。
// 优先级：URL 参数 ?mock=0/1（单次临时切换最便捷）> index.html 的 <meta name="use-mock"> > 缺省 true。
// 缺省 true 是为延续项目既有约定「USE_MOCK=true 完整体验」（无需后端即可完整演示）。
// 联调真实后端时：① 访问 index.html?mock=0 临时切换；② 或把 meta 改为 content="false" 长期生效。
function truthyFalse(v) {
  return v === 'false' || v === '0' || v === 'no' || v === 'off';
}
function readUseMockMeta() {
  if (typeof document === 'undefined') return '';
  const el = document.querySelector('meta[name="use-mock"]');
  return el ? (el.content || '').trim().toLowerCase() : '';
}
function readUseMockQuery() {
  if (typeof window === 'undefined') return null;
  try {
    return new URLSearchParams(window.location.search || '').get('mock');
  } catch (e) {
    return null;
  }
}
function computeUseMock() {
  const q = readUseMockQuery();
  if (q !== null && q !== '') return !truthyFalse(q.toLowerCase());
  const m = readUseMockMeta();
  if (m !== '') return !truthyFalse(m);
  return true;
}
export const USE_MOCK = computeUseMock();

// R-23 生产化 · 接口基址外置：优先读取 index.html 的 <meta name="api-base">，缺省回退本地后端。
// 生产部署时在该 meta 写入真实域名（如 https://api.example.com），无需改代码重新打包。
const API_BASE_META = (typeof document !== 'undefined')
  ? (document.querySelector('meta[name="api-base"]')?.content || '').trim()
  : '';
export const BASE = API_BASE_META || 'http://localhost:8080';
const TOKEN_KEY = 'idlefish_admin_token';

// R-23 生产化 · 令牌存储由 localStorage 改为 sessionStorage：
// 会话级、关标签页即失效，显著缩短 XSS 窃取后的可利用窗口（配合 index.html 的 CSP 进一步收敛 XSS 面）。
// 注：真正的「XSS 不可窃取」需后端 HttpOnly Cookie（已镜像 F-04 在 AdminAuthController 下发），
// 跨源场景下需同源部署或显式 CORS（与用户侧 F-04 一致），详见交付说明。
export function getToken() { return sessionStorage.getItem(TOKEN_KEY) || ''; }
export function setToken(t) { sessionStorage.setItem(TOKEN_KEY, t); }
export function clearToken() { sessionStorage.removeItem(TOKEN_KEY); }

// 统一 HTTP 实例（R-15：401/过期集中处理 —— 清除令牌并回到登录页）
// withCredentials：同源部署下携带后端下发的 HttpOnly 管理员 Cookie（R-23 / 镜像 F-04）。
const http = axios.create({ baseURL: BASE, timeout: 15000, withCredentials: true });
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
