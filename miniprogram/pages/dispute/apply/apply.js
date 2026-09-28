// pages/dispute/apply/apply.js —— 发起售后维权（仅订单买家）
const api = require('../../../utils/api.js');
const { formatPrice, yuanToFen, disputeTypeText, disputeExpectText } = require('../../../utils/util.js');

const REASON_MAX = 200;

// 争议类型（对齐 DisputeService.VALID_TYPES）
const TYPES = [
  { value: 'REFUND_REJECTED', label: '退款被拒' },
  { value: 'NOT_RECEIVED', label: '未收到货' },
  { value: 'DAMAGED', label: '商品破损' },
  { value: 'NOT_AS_DESC', label: '与描述不符' }
];
// 维权诉求（对齐 DisputeService.VALID_EXPECTS）
const EXPECTS = [
  { value: 'REFUND', label: '仅退款' },
  { value: 'RETURN_REFUND', label: '退货退款' }
];

Page({
  data: {
    orderNo: '',
    order: null,
    maxFen: 0,
    maxText: '0.00',
    amount: '',           // 用户输入金额（元），留空则默认全额
    type: 'REFUND_REJECTED',
    expectation: 'REFUND',
    reason: '',
    reasonLen: 0,
    reasonMax: REASON_MAX,
    submitting: false,
    eligible: false,
    ineligibleTip: '',
    types: TYPES,
    expects: EXPECTS
  },

  onLoad(options) {
    this.setData({ orderNo: options.orderNo || '' });
    this.loadOrder();
  },

  loadOrder() {
    if (!this.data.orderNo) {
      wx.showToast({ title: '缺少订单号', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '加载中' });
    api.getOrder(this.data.orderNo).then((o) => {
      wx.hideLoading();
      if (!o) {
        this.setData({ eligible: false, ineligibleTip: '订单不存在' });
        return;
      }
      // 仅订单买家可发起维权（后端 DisputeService.create 守卫）
      const eligible = o.role === 'buyer';
      const maxFen = Number(o.amount) || 0;
      this.setData({
        order: o,
        maxFen: maxFen,
        maxText: formatPrice(maxFen),
        amount: maxFen > 0 ? formatPrice(maxFen) : '',
        eligible: eligible,
        ineligibleTip: eligible ? '' : '仅订单买家可发起维权'
      });
      if (!eligible) {
        wx.showToast({ title: '仅订单买家可发起维权', icon: 'none' });
      }
    }).catch((e) => {
      wx.hideLoading();
      wx.showToast({ title: (e && e.msg) || '订单加载失败', icon: 'none' });
      this.setData({ eligible: false, ineligibleTip: '订单加载失败，请下拉重试' });
    });
  },

  onType(e) { this.setData({ type: e.currentTarget.dataset.type }); },
  onExpect(e) { this.setData({ expectation: e.currentTarget.dataset.expect }); },
  onReason(e) {
    const v = e.detail.value || '';
    this.setData({ reason: v, reasonLen: v.length });
  },
  onAmount(e) { this.setData({ amount: e.detail.value }); },
  onFillAll() {
    if (this.data.maxFen <= 0) return;
    this.setData({ amount: this.data.maxText });
  },
  onAmountBlur() {
    const fen = yuanToFen(this.data.amount);
    if (fen > this.data.maxFen) {
      wx.showToast({ title: '最多 ¥' + this.data.maxText, icon: 'none' });
      this.setData({ amount: this.data.maxText });
    }
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.eligible) {
      return wx.showToast({ title: this.data.ineligibleTip || '当前不可发起维权', icon: 'none' });
    }
    const reason = (this.data.reason || '').trim();
    if (!reason) return wx.showToast({ title: '请填写维权说明', icon: 'none' });
    // 金额可选：留空则使用订单全额（后端默认取订单支付金额）
    let fen = -1;
    if (this.data.amount && String(this.data.amount).trim() !== '') {
      fen = yuanToFen(this.data.amount);
      if (fen < 0) return wx.showToast({ title: '请输入有效的维权金额', icon: 'none' });
      if (fen > this.data.maxFen) return wx.showToast({ title: '最多 ¥' + this.data.maxText, icon: 'none' });
    }
    const payload = {
      orderNo: this.data.orderNo,
      type: this.data.type,
      expectation: this.data.expectation,
      reason: reason,
      amount: fen > 0 ? fen : undefined
    };

    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中' });
    api.createDispute(payload).then((r) => {
      wx.hideLoading();
      const id = r && (r.id || r);
      wx.showToast({ title: '维权已发起', icon: 'success' });
      api.track('dispute_create', { order_no: this.data.orderNo, type: this.data.type, expectation: this.data.expectation });
      setTimeout(() => {
        this.setData({ submitting: false });
        if (id) wx.redirectTo({ url: '/pages/dispute/detail/detail?id=' + id });
        else wx.navigateBack();
      }, 800);
    }).catch((e) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '提交失败', icon: 'none' });
    });
  }
});
