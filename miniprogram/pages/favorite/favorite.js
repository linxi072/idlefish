const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

// 我的收藏列表：分页加载，支持下拉刷新与上拉加载更多
Page({
  data: {
    list: [], loading: true, page: 1, size: 20, total: 0, finished: false
  },

  onLoad() { this.loadList(); },

  onPullDownRefresh() { this.reload(() => wx.stopPullDownRefresh()); },

  onReachBottom() { this.loadMore(); },

  reload(done) {
    this.setData({ page: 1, list: [], finished: false });
    this.loadList(done);
  },

  loadList(done) {
    if (this.data.finished) { done && done(); return; }
    this.setData({ loading: true });
    api.favoriteList(this.data.page, this.data.size).then((r) => {
      const items = (r.items || []).map((i) => {
        i.priceText = formatPrice(i.price);
        i.cover = i.cover || (i.images && i.images[0]) || '';
        return i;
      });
      const list = this.data.page === 1 ? items : this.data.list.concat(items);
      const finished = list.length >= (r.total || 0);
      this.setData({ list, total: r.total || 0, finished, loading: false, page: this.data.page + 1 });
      done && done();
    }).catch((e) => {
      this.setData({ loading: false });
      wx.showToast({ title: (e && e.msg) || '加载失败', icon: 'none' });
      done && done();
    });
  },

  loadMore() { if (!this.data.loading && !this.data.finished) this.loadList(); },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + e.currentTarget.dataset.id });
  }
});
