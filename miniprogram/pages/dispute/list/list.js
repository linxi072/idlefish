// pages/dispute/list/list.js —— 我的维权（买家/卖家视角，按创建倒序）
const api = require('../../../utils/api.js');
const { formatPrice, formatTime, disputeStatusText, disputeTypeText, isDisputeTerminal } = require('../../../utils/util.js');

Page({
  data: {
    list: [],
    total: 0,
    loading: true,
    page: 1,
    size: 20,
    errorTip: ''
  },

  onLoad() { this.load(); },
  onShow() { if (this.data.list.length === 0 && !this.data.loading) this.load(); },
  onPullDownRefresh() { this.load(true); },

  load(refresh) {
    this.setData({ loading: true, errorTip: '' });
    api.myDisputes(this.data.page, this.data.size).then((p) => {
      const records = (p && p.records) || [];
      const list = records.map((d) => Object.assign({}, d, {
        amountText: formatPrice(d.amount),
        statusT: disputeStatusText(d.status),
        typeT: disputeTypeText(d.type),
        timeT: formatTime(d.createdAt),
        terminal: isDisputeTerminal(d.status)
      }));
      this.setData({ list, total: (p && p.total) || 0, loading: false });
      if (refresh) wx.stopPullDownRefresh();
    }).catch((e) => {
      this.setData({ loading: false, errorTip: (e && e.msg) || '加载失败' });
      if (refresh) wx.stopPullDownRefresh();
    });
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (id) wx.navigateTo({ url: '/pages/dispute/detail/detail?id=' + id });
  },

  goApply() { wx.navigateTo({ url: '/pages/dispute/apply/apply' }); }
});
