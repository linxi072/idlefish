const api = require('../../../utils/api.js');
const { formatPrice } = require('../../../utils/util.js');

// 倒计时文案（endAt 为后端时间字符串/时间戳）
function countdownText(endAt, now) {
  const diff = new Date(endAt).getTime() - now;
  if (diff <= 0) return '已结束';
  const d = Math.floor(diff / 86400000);
  const h = Math.floor((diff % 86400000) / 3600000);
  const m = Math.floor((diff % 3600000) / 60000);
  const s = Math.floor((diff % 60000) / 1000);
  if (d > 0) return d + '天' + h + '时' + m + '分';
  return (h < 10 ? '0' + h : h) + ':' + (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
}

// 商品原价（兼容真实后端 priceFen 与本地 mock price）
function itemPrice(it) {
  return (it.priceFen != null ? it.priceFen : it.price) || 0;
}

Page({
  data: {
    list: [], loading: false, loadErr: false, now: Date.now()
  },

  onLoad() {
    this.load();
    // 统一 1s 心跳：重算每张卡片倒计时（活动列表为只读展示，开销可控）
    this.timer = setInterval(() => {
      const now = Date.now();
      const list = this.data.list.map((a) => Object.assign({}, a, { countdownText: countdownText(a.endAt, now) }));
      this.setData({ now, list });
    }, 1000);
  },

  onUnload() { if (this.timer) clearInterval(this.timer); },

  load() {
    this.setData({ loading: true, loadErr: false });
    api.activityList().then((acts) => {
      // 富化商品信息（Activity 仅含 itemId，对齐真实后端，前端按 itemId 拉取详情）
      const tasks = (acts || []).map((a) => {
        const base = {
          typeText: a.type === 'SECKILL' ? '限时秒杀' : '拼团',
          typeClass: a.type === 'SECKILL' ? 'seckill' : 'group',
          priceText: formatPrice(a.activityPrice),
          soldPercent: a.stock > 0 ? Math.min(100, Math.round((a.soldCount || 0) / a.stock * 100)) : 0,
          countdownText: countdownText(a.endAt, Date.now())
        };
        return api.getItemDetail(a.itemId)
          .then((it) => Object.assign({}, a, base, {
            cover: (it.images && it.images[0]) || it.img,
            title: it.title,
            originText: formatPrice(itemPrice(it))
          }))
          .catch(() => Object.assign({}, a, base, {
            cover: '', title: '商品 #' + a.itemId, originText: ''
          }));
      });
      Promise.all(tasks).then((list) => this.setData({ list, loading: false, loadErr: false }));
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  onPullDownRefresh() { this.load(); wx.stopPullDownRefresh(); },

  goDetail(e) { wx.navigateTo({ url: '/pages/activity/detail/detail?id=' + e.currentTarget.dataset.id }); }
});
