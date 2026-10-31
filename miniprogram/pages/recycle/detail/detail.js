// pages/recycle/detail/detail.js —— 回收单详情 / 报价确认 / 取消（F-05，对齐 RecycleStateMachine）
const api = require('../../../utils/api.js');
const { formatPrice, formatTime, recycleStatusText, recyclePickupText, conditionText } = require('../../../utils/util.js');

function toast(msg) { wx.showToast({ title: msg || '操作失败', icon: 'none' }); }

Page({
  data: {
    id: '',
    order: null,
    loading: true,
    errorTip: '',
    canCancel: false,
    canRespond: false,
    submitting: false
  },

  onLoad(options) {
    this.setData({ id: options.id || '' });
    this.loadDetail();
  },

  onShow() { if (this.data.id && !this.data.order) this.loadDetail(); },

  loadDetail() {
    if (!this.data.id) { this.setData({ loading: false, errorTip: '缺少回收单号' }); return; }
    if (!this.data.order) this.setData({ loading: true });
    api.recycleDetail(this.data.id).then((o) => {
      if (!o) { this.setData({ loading: false, errorTip: '回收单不存在' }); return; }
      this.applyOrder(o);
    }).catch((e) => {
      this.setData({ loading: false, errorTip: (e && e.msg) || '加载失败' });
      toast((e && e.msg) || '加载失败');
    });
  },

  applyOrder(o) {
    const status = o.status;
    this.setData({
      order: Object.assign({}, o, {
        statusText: recycleStatusText(status),
        pickupText: recyclePickupText(o.pickupType),
        conditionText: conditionText(o.conditionLevel),
        estimateText: formatPrice(o.estimateAmount || 0),
        finalText: o.finalAmount != null ? formatPrice(o.finalAmount) : '',
        createdText: formatTime(o.createdAt),
        quoteText: formatTime(o.quotedAt),
        paidText: formatTime(o.paidAt)
      }),
      loading: false,
      errorTip: '',
      // 可用操作（严格对齐 RecycleStateMachine 守卫）
      canCancel: status === 'PENDING_PICKUP',            // REQ-03 待上门阶段可无费用取消
      canRespond: status === 'QUOTED'                    // REQ-04/REQ-05 待确认报价
    });
  },

  doAction(title, content, fn, successTip) {
    if (this.data.submitting) return;
    wx.showModal({
      title, content,
      success: (c) => {
        if (!c.confirm) return;
        this.setData({ submitting: true });
        wx.showLoading({ title: '处理中' });
        fn().then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: successTip || '操作成功', icon: 'success' });
          this.loadDetail();
        }).catch((e) => {
          wx.hideLoading();
          this.setData({ submitting: false });
          toast((e && e.msg) || '操作失败');
          this.loadDetail();
        });
      }
    });
  },

  cancelRecycle() {
    this.doAction('取消回收', '待上门阶段取消不收取任何费用，确定取消吗？',
      () => api.recycleCancel(this.data.id, '用户主动取消'), '已取消');
  },

  acceptQuote() {
    this.doAction('接受报价', '确认接受质检报价 ¥' + (this.data.order && this.data.order.finalText) + '？',
      () => api.recycleRespond(this.data.id, true), '已接受报价');
  },

  rejectQuote() {
    this.doAction('拒绝报价', '拒绝后回收单将退回取消，平台不强制成交、不扣费，确定吗？',
      () => api.recycleRespond(this.data.id, false), '已拒绝报价');
  },

  goApply() { wx.navigateTo({ url: '/pages/recycle/apply/apply' }); }
});
