const api = require('../../utils/api.js');
const { fromNow, ITEM_STATUS } = require('../../utils/util.js');

// 审核状态中文（对齐 Item.auditStatus：pending / pass / reject）
const AUDIT_LABEL = { pending: '审核中', pass: '已通过', reject: '已驳回' };

// 我的发布列表：分页加载，支持下拉刷新与上拉加载更多（对齐 api.myItems / ItemController.mine）
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

  // 归一化：mock 返回数组；真实模式返回 IPage<ItemVO>（{records,total}）
  normalize(r) {
    if (Array.isArray(r)) return { arr: r, total: r.length };
    if (r && r.records) return { arr: r.records, total: r.total || 0 };
    if (r && r.list) return { arr: r.list, total: r.total || 0 };
    return { arr: [], total: 0 };
  },

  loadList(done) {
    if (this.data.finished) { done && done(); return; }
    this.setData({ loading: true });
    api.myItems().then((r) => {
      const { arr, total } = this.normalize(r);
      const items = arr.map((i) => {
        // 真实数据用 priceYuan（元）；mock 数据 price 即元，直接展示
        const yuan = i.priceYuan != null ? i.priceYuan : (i.price != null ? i.price : 0);
        return Object.assign({}, i, {
          priceText: '¥' + Number(yuan).toFixed(2),
          cover: i.cover || i.img || (i.images && i.images[0]) || '',
          statusLabel: ITEM_STATUS[i.status] || i.status || '未知',
          auditLabel: AUDIT_LABEL[i.auditStatus] || i.auditStatus || '',
          createdText: fromNow(i.createdAt)
        });
      });
      const list = this.data.page === 1 ? items : this.data.list.concat(items);
      const finished = list.length >= total;
      this.setData({ list, total, finished, loading: false, page: this.data.page + 1 });
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
  },

  goPublish() { wx.navigateTo({ url: '/pages/publish/publish' }); }
});
