// pages/recycle/my/my.js —— 我的回收单列表（F-05，对齐 /api/local/recycle/my）
const api = require('../../../utils/api.js');
const { formatPrice, fromNow, recycleStatusText, recyclePickupText, conditionText } = require('../../../utils/util.js');

Page({
  data: {
    list: [], loading: true, loadErr: false, page: 1, size: 20, finished: false
  },

  onShow() { this.loadAll(true); },

  loadAll(reset) {
    if (reset) this.setData({ list: [], page: 1, finished: false, loading: true, loadErr: false });
    const page = reset ? 1 : this.data.page;
    api.recycleMy(page, this.data.size).then((p) => {
      const records = (p.records || []).map((o) => Object.assign({}, o, {
        statusText: recycleStatusText(o.status),
        pickupText: recyclePickupText(o.pickupType),
        conditionText: conditionText(o.conditionLevel),
        estimateText: formatPrice(o.estimateAmount || 0),
        finalText: o.finalAmount != null ? formatPrice(o.finalAmount) : '',
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
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/recycle/detail/detail?id=' + id });
  },

  goApply() { wx.navigateTo({ url: '/pages/recycle/apply/apply' }); },

  onPullDownRefresh() { this.loadAll(true); wx.stopPullDownRefresh(); }
});
