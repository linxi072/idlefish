const api = require('../../utils/api.js');
const { formatPrice, statusText } = require('../../utils/util.js');

Page({
  data: {
    keyword: '', cats: [], left: [], right: [],
    page: 1, size: 10, total: 0, loading: false, hasMore: true, focus: false
  },

  onLoad() { this.loadCats(); this.loadFeeds(true); },
  onShow() { api.track('app_launch', { channel: 'home' }); },

  loadCats() {
    api.getCategoryTree().then((tree) => {
      const flat = tree.map(c => ({ id: c.id, name: c.name, icon: c.name.charAt(0) }));
      this.setData({ cats: flat.slice(0, 8) });
    }).catch(() => {});
  },

  loadFeeds(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page;
    this.setData({ loading: true });
    api.search({ keyword: this.data.keyword, page, size: this.data.size }).then((r) => {
      const list = (r.items || []).map(i => Object.assign({}, i, {
        priceText: formatPrice(i.price),
        statusT: statusText('item', i.status)
      }));
      let left = reset ? [] : this.data.left;
      let right = reset ? [] : this.data.right;
      list.forEach((it) => { (left.length <= right.length ? left : right).push(it); });
      const count = left.length + right.length;
      this.setData({
        left, right, page: page + 1, total: r.total,
        hasMore: count < r.total, loading: false
      });
    }).catch(() => { this.setData({ loading: false }); });
  },

  onInput(e) { this.setData({ keyword: e.detail.value }); },
  onSearch() { this.loadFeeds(true); },
  onReachBottom() { if (this.data.hasMore) this.loadFeeds(false); },

  goDetail(e) { wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + e.currentTarget.dataset.id }); },
  goCategory(e) { wx.navigateTo({ url: '/pages/category/category?categoryId=' + (e.currentTarget.dataset.id || '') }); },
  goPublish() { wx.navigateTo({ url: '/pages/publish/publish' }); },
  goSearch() { this.setData({ focus: true }); },
  goMessage() { wx.switchTab({ url: '/pages/message/message' }); }
});
