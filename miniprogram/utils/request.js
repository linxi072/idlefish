// request.js —— 基于 wx.request 的封装：统一 baseUrl、JWT、响应解析、401 续期、失败跳登录
const store = require('./store.js');

function buildHeader(auth) {
  const header = { 'content-type': 'application/json' };
  if (auth) {
    const app = getApp();
    if (app && app.globalData.token) {
      header['Authorization'] = 'Bearer ' + app.globalData.token;
    }
  }
  return header;
}

// 刷新令牌：串行化并发 401，避免重复刷新；成功后回写全局与本地存储
let refreshingPromise = null;
function refreshToken() {
  if (refreshingPromise) return refreshingPromise;
  refreshingPromise = new Promise((resolve, reject) => {
    const rt = store.getRefreshToken();
    if (!rt) { reject({ needLogin: true }); return; }
    const app = getApp();
    const url = (app.globalData.apiBaseUrl || '') + '/api/auth/refresh';
    wx.request({
      url, method: 'POST', header: { 'content-type': 'application/json' },
      data: { refreshToken: rt },
      success(res) {
        const b = res.data;
        if (res.statusCode === 200 && b && b.code === 0 && b.data && b.data.token) {
          const a = getApp();
          a.globalData.token = b.data.token;
          store.setToken(b.data.token);
          if (b.data.refreshToken) {
            a.globalData.refreshToken = b.data.refreshToken;
            store.setRefreshToken(b.data.refreshToken);
          }
          resolve(b.data.token);
        } else {
          reject({ needLogin: true });
        }
      },
      fail() { reject({ needLogin: true }); }
    });
  }).finally(() => { refreshingPromise = null; });
  return refreshingPromise;
}

function request(method, path, data, auth, retried) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const url = (app.globalData.apiBaseUrl || '') + path;
    wx.request({
      url, method, data: data || {}, header: buildHeader(auth),
      timeout: 15000,
      success(res) {
        const body = res.data;
        if (res.statusCode === 200 && body && typeof body === 'object') {
          if (body.code === 0) { resolve(body.data); }
          else if (body.code === 20001 || body.code === 20002) {
            // 未登录 / token 过期：尝试用 refresh token 续期并重试一次
            if (auth !== false && !retried) {
              refreshToken().then(() => {
                request(method, path, data, auth, true).then(resolve).catch(reject);
              }).catch(() => {
                if (app && app.redirectToLogin) app.redirectToLogin();
                reject({ code: body.code, msg: body.msg || '请先登录' });
              });
            } else {
              if (app && app.redirectToLogin) app.redirectToLogin();
              reject({ code: body.code, msg: body.msg || '请先登录' });
            }
          } else {
            reject({ code: body.code, msg: body.msg || '请求失败' });
          }
        } else {
          reject({ code: res.statusCode, msg: '网络错误(' + res.statusCode + ')' });
        }
      },
      fail(err) {
        reject({ code: -1, msg: '网络连接失败', err });
      }
    });
  });
}

const http = {
  get: (path, data, auth) => request('GET', path, data, auth !== false),
  post: (path, data, auth) => request('POST', path, data, auth !== false),
  put: (path, data, auth) => request('PUT', path, data, auth !== false),
  del: (path, data, auth) => request('DELETE', path, data, auth !== false),
  // 文件上传：封装 wx.uploadFile，统一鉴权头与响应解析（含 401 续期）
  upload: (filePath, name, formData) => upload(filePath, name, formData)
};

function upload(filePath, name, formData) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const url = (app.globalData.apiBaseUrl || '') + '/api/file/upload';
    const doUpload = () => wx.uploadFile({
      url, filePath, name: name || 'file', formData: formData || {},
      header: (app && app.globalData.token) ? { Authorization: 'Bearer ' + app.globalData.token } : {},
      success(res) {
        if (res.statusCode === 200) {
          try {
            const body = JSON.parse(res.data);
            if (body.code === 0) resolve(body.data);
            else if ((body.code === 20001 || body.code === 20002) && !upload._retried) {
              upload._retried = true;
              refreshToken().then(() => { upload._retried = false; doUpload().then(resolve).catch(reject); })
                .catch(() => { upload._retried = false; if (app && app.redirectToLogin) app.redirectToLogin(); reject({ code: body.code, msg: body.msg || '请先登录' }); });
            } else reject({ code: body.code, msg: body.msg || '上传失败' });
          } catch (e) { reject({ code: -1, msg: '响应解析失败' }); }
        } else {
          reject({ code: res.statusCode, msg: '上传失败(' + res.statusCode + ')' });
        }
      },
      fail(err) { reject({ code: -1, msg: '上传失败', err }); }
    });
    doUpload();
  });
}

module.exports = http;
