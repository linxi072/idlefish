const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

// 直播秒杀页（ITER-F08 / REQ-07）：风险告知 → Redis 预扣 + MQ 异步落库 + 降级强一致
Page({
  data: {
    activityId: '',
    activity: null,
    grabbing: false,
    result: null,
    overlay: { show: false, title: '', content: [], confirmText: '' }
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '' });
    this.loadActivity();
  },

  loadActivity() {
    if (!this.data.activityId) return;
    api.mktActivityDetail(this.data.activityId).then((a) => {
      this.setData({
        activity: a ? Object.assign({}, a, {
          priceText: formatPrice(a.priceFen),
          originalText: formatPrice(a.originalPriceFen),
          progress: a.stock > 0 ? Math.min(100, Math.round(((a.soldCount || 0) / a.stock) * 100)) : 0
        }) : null
      });
    }).catch(() => {});
  },

  // 先弹出秒杀风险告知（合规浮层，理性消费），确认后再抢购
  confirmGrab() {
    this.setData({
      overlay: {
        show: true,
        title: '直播秒杀风险告知',
        confirmText: '同意并抢购',
        content: [
          '秒杀商品数量有限，先到先得，售完即止（Redis 预扣 + 数据库强一致兜底）。',
          '抢购成功后请在规定时间内完成支付，超时订单将自动释放库存。',
          '请理性消费，勿轻信非官方渠道的代抢服务。'
        ]
      }
    });
  },

  onOverlayClose() { this.setData({ 'overlay.show': false }); },

  onOverlayConfirm() {
    this.setData({ 'overlay.show': false });
    this.grab();
  },

  // 抢购：ACCEPTED 表示预扣成功，后续由 MQ 异步落库
  grab() {
    if (this.data.grabbing) return;
    this.setData({ grabbing: true });
    api.mktSeckillGrab({ activityId: Number(this.data.activityId) }).then((r) => {
      this.setData({ grabbing: false, result: this.decorate(r) });
      if (r.status === 'ACCEPTED') wx.showToast({ title: '抢购成功', icon: 'success' });
    }).catch((e) => {
      this.setData({ grabbing: false });
      wx.showToast({ title: (e && e.msg) || '抢购失败', icon: 'none' });
    });
  },

  decorate(r) {
    if (!r) return r;
    return Object.assign({}, r, { amountText: formatPrice(r.amountFen || 0) });
  }
});
