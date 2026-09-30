const api = require('../../../utils/api.js');
const { formatPrice } = require('../../../utils/util.js');

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

function itemPrice(it) { return (it.priceFen != null ? it.priceFen : it.price) || 0; }

Page({
  data: {
    id: null, act: null, item: null, joined: false, joining: false, countdownText: ''
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.load();
    this.timer = setInterval(() => {
      const act = this.data.act;
      if (act) this.setData({ countdownText: countdownText(act.endAt, Date.now()) });
    }, 1000);
  },

  onUnload() { if (this.timer) clearInterval(this.timer); },

  load() {
    api.activityDetail(this.data.id).then((act) => {
      if (!act) { this.setData({ act: null }); return; }
      const enriched = Object.assign({}, act, {
        typeText: act.type === 'SECKILL' ? '限时秒杀' : '拼团',
        priceText: formatPrice(act.activityPrice),
        soldPercent: act.stock > 0 ? Math.min(100, Math.round((act.soldCount || 0) / act.stock * 100)) : 0,
        left: Math.max(0, (act.stock || 0) - (act.soldCount || 0))
      });
      this.setData({ act: enriched, countdownText: countdownText(act.endAt, Date.now()) });

      api.getItemDetail(act.itemId).then((it) => {
        this.setData({ item: {
          title: it.title, cover: (it.images && it.images[0]) || it.img,
          originText: formatPrice(itemPrice(it)), seller: it.seller
        } });
      }).catch(() => {});

      api.activityMy().then((mine) => {
        const joined = (mine || []).some((p) => p.activityId === Number(this.data.id));
        this.setData({ joined });
      }).catch(() => {});
    }).catch(() => {});
  },

  onPullDownRefresh() { this.load(); wx.stopPullDownRefresh(); },

  join() {
    const act = this.data.act;
    if (!act || this.data.joining) return;
    if (act.status !== 'ONGOING') { wx.showToast({ title: '活动已结束', icon: 'none' }); return; }
    this.setData({ joining: true });
    // 拼团不传 groupNo = 开新团（真实后端自建团）；秒杀直接锁定
    api.activityJoin({ activityId: act.id, itemId: act.itemId, qty: 1 }).then(() => {
      this.setData({ joining: false, joined: true });
      wx.showToast({ title: act.type === 'GROUP' ? '开团成功' : '抢购成功', icon: 'success' });
      this.load();
    }).catch((e) => {
      this.setData({ joining: false });
      wx.showToast({ title: (e && e.msg) || '参与失败', icon: 'none' });
    });
  }
});
