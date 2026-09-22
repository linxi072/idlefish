const api = require('../../utils/api.js');
const { formatPrice, statusText } = require('../../utils/util.js');

Page({
  data: {
    tree: [], crumb: [], current: { id: null, name: '推荐', children: [] }, subs: [],
    items: [], sort: 'default', page: 1, size: 10, total: 0, hasMore: true, loading: false
  },

  onLoad(options) {
    api.getCategoryTree().then((tree) => {
      this.setData({ tree });
      let node = null;
      if (options.categoryId) {
        tree.forEach(c => {
          const found = findNode(c, Number(options.categoryId));
          if (found) node = found;
        });
      }
      node = node || { id: null, name: '推荐', children: tree };
      this.enterNode(node, []);
      if (options.categoryId && node.id) this.loadItems(true);
    });
  },

  enterNode(node, crumb) {
    this.setData({
      crumb, current: node, subs: node.children || [],
      items: [], page: 1, hasMore: true, total: 0
    });
  },

  onTopTap(e) {
    const id = Number(e.currentTarget.dataset.id);
    const node = this.data.tree.find(c => c.id === id) || { id, name: '', children: [] };
    this.enterNode(node, []);
    this.loadItems(true);
  },

  onSubTap(e) {
    const id = Number(e.currentTarget.dataset.id);
    const node = this.data.subs.find(s => s.id === id);
    if (node && node.children && node.children.length) {
      const crumb = this.data.crumb.concat([this.data.current]);
      this.enterNode(node, crumb);
      this.loadItems(true);
    } else {
      // 叶子：按该类目检索（保持当前层级）
      this.setData({ current: Object.assign({}, this.data.current, { id: node.id, name: node.name }), items: [], page: 1, hasMore: true });
      this.loadItems(true);
    }
  },

  onCrumbTap(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const crumb = this.data.crumb.slice(0, idx);
    const node = crumb[crumb.length - 1] || { id: null, name: '推荐', children: this.data.tree };
    this.enterNode(node, crumb.slice(0, idx - 1 < 0 ? 0 : idx));
    this.loadItems(true);
  },

  changeSort(e) {
    this.setData({ sort: e.currentTarget.dataset.sort, items: [], page: 1, hasMore: true });
    this.loadItems(true);
  },

  loadItems(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page;
    this.setData({ loading: true });
    api.search({ keyword: '', categoryId: this.data.current.id || '', sort: this.data.sort, page, size: this.data.size })
      .then((r) => {
        const list = (r.items || []).map(i => Object.assign({}, i, { priceText: formatPrice(i.price) }));
        const items = reset ? list : this.data.items.concat(list);
        this.setData({ items, page: page + 1, total: r.total, hasMore: items.length < r.total, loading: false });
      })
      .catch(() => this.setData({ loading: false }));
  },

  onReachBottom() { if (this.data.hasMore) this.loadItems(false); },
  goDetail(e) { wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + e.currentTarget.dataset.id }); }
});

function findNode(node, id) {
  if (node.id === id) return node;
  if (node.children) {
    for (const c of node.children) {
      const f = findNode(c, id);
      if (f) return f;
    }
  }
  return null;
}
