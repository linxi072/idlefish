const api = require('../../../utils/api.js');

Page({
  data: {
    benefits: [], page: 1, size: 20, total: 0, loading: false, finished: false, loadErr: false,
    exchangingId: 0
  },

  onShow() { this.loadBenefits(true); },

  loadBenefits(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, loadErr: false });
    api.growthBenefits(page, this.data.size).then((p) => {
      const recs = (p.records || []).map((b) => Object.assign({}, b, {
        soldOut: (b.stock || 0) <= 0
      }));
      const list = reset ? recs : this.data.benefits.concat(recs);
      this.setData({ benefits: list, page, total: p.total || 0, loading: false, finished: list.length >= (p.total || 0) });
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  onReachBottom() { if (!this.data.finished) this.loadBenefits(false); },
  onPullDownRefresh() { this.loadBenefits(true); wx.stopPullDownRefresh(); },

  onExchange(e) {
    const id = e.currentTarget.dataset.id;
    const b = this.data.benefits.find((x) => x.id === id);
    if (!b || !b.exchangeable) { wx.showToast({ title: '当前不可兑换', icon: 'none' }); return; }
    if (this.data.exchangingId) return;
    wx.showModal({
      title: '确认兑换',
      content: '将消耗 ' + b.costGrowth + ' 成长值兑换「' + b.benefitName + '」',
      success: (r) => {
        if (!r.confirm) return;
        this.setData({ exchangingId: id });
        api.growthExchange(id).then((res) => {
          this.setData({ exchangingId: 0 });
          wx.showToast({ title: '兑换成功', icon: 'success' });
          this.loadBenefits(true);
          setTimeout(() => {
            wx.navigateTo({ url: '/pages/growth/exchanges/exchanges' });
          }, 800);
        }).catch((err) => {
          this.setData({ exchangingId: 0 });
          wx.showToast({ title: (err && err.msg) || '兑换失败', icon: 'none' });
        });
      }
    });
  },

  goExchanges() { wx.navigateTo({ url: '/pages/growth/exchanges/exchanges' }); }
});
