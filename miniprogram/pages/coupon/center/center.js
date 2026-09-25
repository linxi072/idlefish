const api = require('../../utils/api.js');
const { formatPrice, couponTypeText, couponDesc, couponScopeText, formatTime } = require('../../utils/util.js');

Page({
  data: {
    list: [], page: 1, size: 10, total: 0, loading: false, finished: false, loadErr: false
  },

  onLoad() { this.loadMore(true); },

  loadMore(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, loadErr: false });
    api.couponCenter(page, this.data.size).then((p) => {
      const recs = (p.records || []).map(c => Object.assign({}, c, {
        typeText: couponTypeText(c.type),
        desc: couponDesc(c),
        scopeText: couponScopeText(c.scope),
        startText: formatTime(c.startAt),
        endText: formatTime(c.endAt),
        stockText: '剩 ' + Math.max(0, (c.totalCount || 0) - (c.claimedCount || 0)) + ' 张'
      }));
      const list = reset ? recs : this.data.list.concat(recs);
      this.setData({ list, page, total: p.total || 0, loading: false, finished: list.length >= (p.total || 0), loadErr: false });
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  onReachBottom() { if (!this.data.finished) this.loadMore(false); },
  onPullDownRefresh() { this.loadMore(true); wx.stopPullDownRefresh(); },

  claim(e) {
    const id = e.currentTarget.dataset.id;
    const c = this.data.list.find(x => x.id === id);
    if (!c || c.claimed || !c.claimable) return;
    wx.showLoading({ title: '领取中' });
    api.couponClaim(id).then(() => {
      wx.hideLoading();
      wx.showToast({ title: '领取成功', icon: 'success' });
      const list = this.data.list.map(x => x.id === id
        ? Object.assign({}, x, { claimed: true, claimedCount: (x.claimedCount || 0) + 1 })
        : x);
      this.setData({ list });
    }).catch((e) => {
      wx.hideLoading();
      wx.showToast({ title: (e && e.msg) || '领取失败', icon: 'none' });
    });
  },

  goMy() { wx.navigateTo({ url: '/pages/coupon/my/my' }); }
});
