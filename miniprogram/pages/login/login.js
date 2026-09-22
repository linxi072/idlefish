const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

Page({
  data: { agreed: false, loading: false },

  toggleAgree() { this.setData({ agreed: !this.data.agreed }); },

  wechatLogin() {
    if (!this.data.agreed) { wx.showToast({ title: '请先阅读并同意协议', icon: 'none' }); return; }
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: (res) => {
        api.login(res.code).then((d) => {
          store.setToken(d.token);
          store.setRefreshToken(d.refreshToken);
          store.setUserInfo(d.user);
          const app = getApp();
          app.globalData.token = d.token;
          app.globalData.userInfo = d.user;
          api.track('user_login', { login_type: 'wechat' });
          wx.showToast({ title: '登录成功', icon: 'success' });
          setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 600);
        }).catch((e) => {
          wx.showToast({ title: (e && e.msg) || '登录失败', icon: 'none' });
        }).finally(() => this.setData({ loading: false }));
      },
      fail: () => {
        wx.showToast({ title: '微信登录失败', icon: 'none' });
        this.setData({ loading: false });
      }
    });
  },

  phoneLogin() {
    if (!this.data.agreed) { wx.showToast({ title: '请先阅读并同意协议', icon: 'none' }); return; }
    wx.showToast({ title: '请在真机体验手机号授权', icon: 'none' });
  }
});
