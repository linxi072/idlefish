// settings.js —— 设置页（账户与安全 / 通用设置 / 退出登录）
const store = require('../../utils/store.js');

Page({
  data: {
    user: { nickname: '未登录', avatar: '', creditScore: 0, phone: '' },
    notifyOn: true,
    version: 'V1.0.4',
    appName: '闲置集'
  },

  onShow() {
    const u = store.getUserInfo();
    if (u) this.setData({ user: u });
    // 消息推送开关：默认开启（本地 storage 占位，后端未接推送通道）
    const n = wx.getStorageSync('setting_notify');
    this.setData({ notifyOn: n === '' ? true : !!n });
  },

  onNotifyChange(e) {
    const v = e.detail.value;
    wx.setStorageSync('setting_notify', v);
    this.setData({ notifyOn: v });
    wx.showToast({ title: v ? '已开启消息推送' : '已关闭消息推送', icon: 'none' });
  },

  onAbout() {
    wx.showModal({
      title: '关于' + this.data.appName,
      content: this.data.appName + ' · 校园/邻里闲置交易小程序\n版本 ' + this.data.version + '\n让闲置流动，让信任连接。',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  onAgreement() {
    wx.showToast({ title: '用户协议（建设中）', icon: 'none' });
  },

  onPrivacy() {
    wx.showToast({ title: '隐私政策（建设中）', icon: 'none' });
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: (r) => {
        if (r.confirm) {
          store.clearToken();
          store.clearUser();
          const app = getApp();
          app.globalData.token = '';
          app.globalData.userInfo = null;
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  }
});
