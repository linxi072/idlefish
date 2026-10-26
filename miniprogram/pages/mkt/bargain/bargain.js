const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

// 砍价页（ITER-F08 / REQ-08、REQ-09）：发起砍价会话 → 好友助力逐刀 → 成团
Page({
  data: {
    activityId: '',
    activity: null,
    session: null,
    submitting: false
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
          originalText: formatPrice(a.originalPriceFen)
        }) : null
      });
    }).catch(() => {});
  },

  // 发起砍价：占用 1 内核名额 + 建 BargainSession
  create() {
    if (!this.data.activityId || this.data.submitting) return;
    this.setData({ submitting: true });
    api.mktBargainCreate({ activityId: Number(this.data.activityId) }).then((s) => {
      this.setData({ submitting: false, session: this.decorate(s) });
      wx.showToast({ title: '砍价发起成功', icon: 'success' });
    }).catch((e) => {
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '发起失败', icon: 'none' });
    });
  },

  // 好友助力：逐刀纯函数算额 + 成团判定（演示态下模拟一位好友助力）
  assist() {
    const s = this.data.session;
    if (!s || this.data.submitting) return;
    this.setData({ submitting: true });
    api.mktBargainAssist({ sessionNo: s.sessionNo }).then((ns) => {
      this.setData({ submitting: false, session: this.decorate(ns) });
      if (ns.qualified) wx.showToast({ title: '已砍到底价，可下单', icon: 'success' });
      else wx.showToast({ title: '助力成功', icon: 'success' });
    }).catch((e) => {
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '助力失败', icon: 'none' });
    });
  },

  decorate(s) {
    if (!s) return s;
    const total = (s.originalPriceFen || 0) - (s.floorPriceFen || 0);
    const cut = (s.originalPriceFen || 0) - (s.currentPriceFen || 0);
    return Object.assign({}, s, {
      currentText: formatPrice(s.currentPriceFen),
      floorText: formatPrice(s.floorPriceFen),
      originalText: formatPrice(s.originalPriceFen),
      cutText: formatPrice(cut),
      progress: total > 0 ? Math.min(100, Math.round((cut / total) * 100)) : 0,
      statusText: s.qualified ? '已成团' : (s.status === 'QUALIFIED' ? '已成团' : '砍价中'),
      assists: (s.assists || []).map((x) => Object.assign({}, x, { amountText: formatPrice(x.assistAmountFen) }))
    });
  }
});
