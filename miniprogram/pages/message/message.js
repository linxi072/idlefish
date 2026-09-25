const api = require('../../utils/api.js');
const { fromNow, formatPrice } = require('../../utils/util.js');

Page({
  data: { list: [], loading: true },

  onShow() {
    this.setData({ loading: true });
    api.getConversations().then((list) => {
      const items = (list || []).map(c => Object.assign({}, c, { timeT: fromNow(c.updatedAt) }));
      this.setData({ list: items, loading: false });
      this.refreshTabBadge(items);
    }).catch(() => this.setData({ loading: false }));
  },

  // IM 未读总数 → 消息 Tab 角标（tabBar 索引 2）
  refreshTabBadge(list) {
    const total = (list || []).reduce((s, c) => s + (c.unread || 0), 0);
    if (total > 0) {
      wx.setTabBarBadge({ index: 2, text: total > 99 ? '99+' : String(total) }).catch(() => {});
    } else {
      wx.removeTabBarBadge({ index: 2 }).catch(() => {});
    }
  },

  goChat(e) {
    const c = e.currentTarget.dataset.c;
    api.track('im_session_start', { conv_id: c.convId, item_id: c.item.id, peer_id: c.peer.id });
    // RK-1：聊天卡价格统一以「元」字符串传递（chat.wxml 直接 ¥{{itemPrice}} 展示），
    // 严禁传原始「分」值，否则会出现 100 倍金额展示资损。
    const itemPrice = c.item.priceText || formatPrice(c.item.price);
    wx.navigateTo({
      url: `/pages/chat/chat?convId=${c.convId}&peerId=${c.peer.id}&itemId=${c.item.id}&itemTitle=${encodeURIComponent(c.item.title)}&itemImg=${c.item.img}&itemPrice=${encodeURIComponent(itemPrice)}`
    });
  }
});
