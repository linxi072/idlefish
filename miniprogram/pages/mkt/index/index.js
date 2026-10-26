const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

const KIND_TABS = [
  { key: '', name: '全部' },
  { key: 'BARGAIN', name: '砍价' },
  { key: 'LOTTERY', name: '抽奖' },
  { key: 'SECKILL', name: '秒杀' }
];
const KIND_NAME = { BARGAIN: '砍价', LOTTERY: '抽奖', SECKILL: '秒杀' };
const KIND_PAGE = { BARGAIN: 'bargain', LOTTERY: 'lottery', SECKILL: 'seckill' };

Page({
  data: {
    tabs: KIND_TABS,
    activeKind: '',
    activities: [],
    loading: false
  },

  onShow() { this.load(); },

  switchTab(e) {
    const kind = e.currentTarget.dataset.kind;
    if (kind === this.data.activeKind) return;
    this.setData({ activeKind: kind });
    this.load();
  },

  load() {
    this.setData({ loading: true });
    api.mktActivityList(this.data.activeKind).then((list) => {
      const acts = (list || []).map((a) => Object.assign({}, a, {
        kindName: KIND_NAME[a.kind] || a.kind,
        priceText: formatPrice(a.priceFen),
        originalText: formatPrice(a.originalPriceFen),
        progress: a.stock > 0 ? Math.min(100, Math.round(((a.soldCount || 0) / a.stock) * 100)) : 0
      }));
      this.setData({ activities: acts, loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  goPlay(e) {
    const { id, kind } = e.currentTarget.dataset;
    const page = KIND_PAGE[kind] || 'detail';
    wx.navigateTo({ url: `/pages/mkt/${page}/${page}?activityId=${id}` });
  }
});
