// store.js —— 登录态本地存储（token / userInfo）
const TOKEN_KEY = 'idlefish_token';
const USER_KEY = 'idlefish_user';
const REFRESH_KEY = 'idlefish_refresh';

function getToken() { return wx.getStorageSync(TOKEN_KEY) || ''; }
function setToken(t) { wx.setStorageSync(TOKEN_KEY, t); }
function getRefreshToken() { return wx.getStorageSync(REFRESH_KEY) || ''; }
function setRefreshToken(t) { wx.setStorageSync(REFRESH_KEY, t); }
function clearToken() { wx.removeStorageSync(TOKEN_KEY); wx.removeStorageSync(REFRESH_KEY); }

function getUserInfo() { return wx.getStorageSync(USER_KEY) || null; }
function setUserInfo(u) { wx.setStorageSync(USER_KEY, u); }
function clearUser() { wx.removeStorageSync(USER_KEY); }

function isLogin() { return !!getToken(); }

module.exports = {
  getToken, setToken, getRefreshToken, setRefreshToken, clearToken,
  getUserInfo, setUserInfo, clearUser, isLogin
};
