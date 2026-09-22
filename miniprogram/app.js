// app.js —— 小程序入口，全局配置与登录态管理
const { getToken, getUserInfo } = require('./utils/store.js');

App({
  globalData: {
    // ===== 后端 API 基地址（联调时改成你的后端地址）=====
    // 真机/模拟器联调：将下方地址改为电脑局域网 IP，例如 http://192.168.1.10:8080
    apiBaseUrl: 'http://localhost:8080',
    // 是否使用本地 Mock 数据（无需后端即可演示）。联调后端时改为 false
    useMock: true,
    token: '',
    userInfo: null,
    // 埋点统一字段
    deviceId: ''
  },

  onLaunch() {
    this.globalData.token = getToken() || '';
    this.globalData.userInfo = getUserInfo();
    // 生成稳定的设备 ID（埋点用）
    let dev = wx.getStorageSync('device_id');
    if (!dev) {
      dev = 'dev_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
      wx.setStorageSync('device_id', dev);
    }
    this.globalData.deviceId = dev;
  },

  // 跳转登录（未登录时调用）
  redirectToLogin() {
    const pages = getCurrentPages();
    const cur = pages[pages.length - 1];
    if (cur && cur.route && cur.route.indexOf('login') === -1) {
      wx.reLaunch({ url: '/pages/login/login' });
    }
  }
});
