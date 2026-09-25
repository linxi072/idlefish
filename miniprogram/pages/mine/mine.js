const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

Page({
  data: {
    user: { nickname: '未登录', avatar: '', creditScore: 0, phone: '' },
    notifUnread: 0,
    menus: [
      { icon: '📦', name: '我的发布', url: '' },
      { icon: '📋', name: '我的订单', url: '/pages/order/list/list' },
      { icon: '💰', name: '我的钱包', url: '/pages/wallet/index/index' },
      { icon: '🎟️', name: '优惠券', url: '/pages/coupon/my/my' },
      { icon: '⭐', name: '我的评价', url: '/pages/evaluate/list/list' },
      { icon: '🔔', name: '系统通知', url: '/pages/notification/notification', badge: true },
      { icon: '📍', name: '收货地址', url: '/pages/address/address' },
      { icon: '💬', name: '消息中心', url: '/pages/message/message' },
      { icon: '⭐', name: '我的收藏', url: '/pages/favorite/favorite' },
      { icon: '⚙️', name: '设置', url: '' }
    ]
  },

  onShow() {
    const u = store.getUserInfo();
    if (u) this.setData({ user: u });
    else api.getUserInfo().then((u2) => { this.setData({ user: u2 }); store.setUserInfo(u2); }).catch(() => {});
    this.loadNotifyUnread();
  },

  // 站内通知未读数（驱动「系统通知」菜单红点）
  loadNotifyUnread() {
    api.notifyUnread().then((n) => this.setData({ notifUnread: n || 0 })).catch(() => {});
  },

  onMenu(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) { wx.showToast({ title: '示例菜单', icon: 'none' }); return; }
    wx.navigateTo({ url });
  },

  goPublish() { wx.navigateTo({ url: '/pages/publish/publish' }); },

  logout() {
    wx.showModal({
      title: '退出登录', content: '确定要退出当前账号吗？', success: (r) => {
        if (r.confirm) {
          store.clearToken(); store.clearUser();
          const app = getApp(); app.globalData.token = ''; app.globalData.userInfo = null;
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  }
});
