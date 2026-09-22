const api = require('../../utils/api.js');
const { formatPrice, statusText, formatTime } = require('../../utils/util.js');

Page({
  data: { role: 'buyer', list: [], loading: true },

  onShow() { this.reload(); },

  reload() {
    this.setData({ loading: true });
    api.listOrders(this.data.role).then((list) => {
      const items = (list || []).map(o => Object.assign({}, o, {
        amountText: formatPrice(o.amount),
        statusT: statusText('order', o.status),
        timeT: formatTime(o.createdAt)
      }));
      this.setData({ list: items, loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  switchRole(e) {
    this.setData({ role: e.currentTarget.dataset.role });
    this.reload();
  },

  goDetail(e) { wx.navigateTo({ url: '/pages/order/detail/detail?orderNo=' + e.currentTarget.dataset.no }); }
});
