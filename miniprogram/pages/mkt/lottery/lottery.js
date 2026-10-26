const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

// 抽奖页（ITER-F08 / REQ-06、REQ-09、REQ-10）：抽奖 → 概率快照 → 实物奖品合规实名
Page({
  data: {
    activityId: '',
    activity: null,
    drawing: false,
    result: null,
    showForm: false,
    realName: '',
    idCard: '',
    submitting: false,
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
          originalText: formatPrice(a.originalPriceFen)
        }) : null
      });
    }).catch(() => {});
  },

  // 抽一次：内核占名额 + 奖品原子扣减 + 概率快照
  draw() {
    if (this.data.drawing) return;
    this.setData({ drawing: true, result: null, showForm: false });
    api.mktLotteryDraw({ activityId: Number(this.data.activityId) }).then((r) => {
      this.setData({ drawing: false, result: this.decorate(r) });
      if (r.won && r.needRealname) this.showRealnameOverlay();
    }).catch((e) => {
      this.setData({ drawing: false });
      wx.showToast({ title: (e && e.msg) || '抽奖失败', icon: 'none' });
    });
  },

  decorate(r) {
    if (!r) return r;
    return Object.assign({}, r, {
      couponText: r.couponAmountFen ? formatPrice(r.couponAmountFen) : ''
    });
  },

  // 实物奖品领取须知（合规浮层，REQ-10）
  showRealnameOverlay() {
    this.setData({
      overlay: {
        show: true,
        title: '实物奖品领取须知',
        confirmText: '我已知晓，去填写',
        content: [
          '根据《个人信息保护法》，领取实物奖品需提供收货人姓名与身份证号，用于发货与合规核验。',
          '身份证号将脱敏存储（仅保留前 6 位与后 4 位），不会用于任何无关用途。',
          '请确保信息真实有效，虚假信息将导致奖品作废且可能被风控拦截。'
        ]
      }
    });
  },

  onOverlayClose() { this.setData({ 'overlay.show': false }); },

  // 确认须知后，展示内嵌实名表单
  onOverlayConfirm() {
    this.setData({ 'overlay.show': false, showForm: true });
  },

  onRealNameInput(e) { this.setData({ realName: e.detail.value }); },
  onIdCardInput(e) { this.setData({ idCard: e.detail.value }); },

  submitRealname() {
    const r = this.data.result;
    if (!this.data.realName || !this.data.idCard) {
      wx.showToast({ title: '请完整填写收货信息', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    api.mktLotteryRealname({
      recordId: r.recordId, realName: this.data.realName, idCard: this.data.idCard
    }).then(() => {
      this.setData({ submitting: false, showForm: false });
      wx.showToast({ title: '提交成功，奖品将尽快发出', icon: 'success' });
    }).catch((e) => {
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '提交失败', icon: 'none' });
    });
  }
});
