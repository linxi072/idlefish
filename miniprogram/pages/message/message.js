const api = require('../../utils/api.js');
const { fromNow } = require('../../utils/util.js');

Page({
  data: { list: [], loading: true },

  onShow() {
    this.setData({ loading: true });
    api.getConversations().then((list) => {
      const items = (list || []).map(c => Object.assign({}, c, { timeT: fromNow(c.updatedAt) }));
      this.setData({ list: items, loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  goChat(e) {
    const c = e.currentTarget.dataset.c;
    api.track('im_session_start', { conv_id: c.convId, item_id: c.item.id, peer_id: c.peer.id });
    wx.navigateTo({
      url: `/pages/chat/chat?convId=${c.convId}&peerId=${c.peer.id}&itemId=${c.item.id}&itemTitle=${encodeURIComponent(c.item.title)}&itemImg=${c.item.img}&itemPrice=${c.item.price}`
    });
  }
});
