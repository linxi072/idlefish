// pages/delivery/my/my.js —— 我的同城配送列表（F-05，对齐 /api/local/delivery/my）
const api = require('../../../utils/api.js');
const { formatPrice, fromNow, deliveryStatusText, deliveryBizText } = require('../../../utils/util.js');

Page({
  data: {
    list: [], loading: true, loadErr: false, page: 1, size: 20, finished: false
  },

  onShow() { this.loadAll(true); },

  loadAll(reset) {
    if (reset) this.setData({ list: [], page: 1, finished: false, loading: true, loadErr: false });
    const page = reset ? 1 : this.data.page;
    api.deliveryMy(page, this.data.size).then((p) => {
      const records = (p.records || []).map((o) => Object.assign({}, o, {
        statusText: deliveryStatusText(o.status),
        bizText: deliveryBizText(o.bizType),
        freightText: formatPrice(o.freight || 0),
        timeText: fromNow(o.createdAt)
      }));
      const list = this.data.list.concat(records);
      this.setData({
        list,
        loading: false,
        finished: list.length >= (p.total || 0),
        page: page + 1
      });
    }).catch(() => {
      this.setData({ loading: false, loadErr: true });
    });
  },

  loadMore() {
    if (this.data.loading || this.data.finished) return;
    this.loadAll(false);
  },

  goDetail(e) {
    const no = e.currentTarget.dataset.no;
    wx.navigateTo({ url: '/pages/delivery/detail/detail?deliveryNo=' + no });
  },

  goApply() { wx.navigateTo({ url: '/pages/delivery/apply/apply' }); },

  onPullDownRefresh() { this.loadAll(true); wx.stopPullDownRefresh(); }
});
