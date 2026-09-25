// pages/refund/apply/apply.js —— 申请退款（仅退款 / 退货退款）
const api = require('../../../utils/api.js');
const { formatPrice, yuanToFen, canApplyRefund, statusText } = require('../../../utils/util.js');

const REASON_MAX = 200;

Page({
  data: {
    orderNo: '',
    order: null,
    maxFen: 0,          // 最大可退金额（分）
    maxText: '0.00',
    amount: '',         // 用户输入的金额（元）
    type: 'only_refund',
    reason: '',
    reasonLen: 0,
    reasonMax: REASON_MAX,
    submitting: false,
    eligible: false,    // 订单是否允许申请退款（对齐后端 RefundService.apply 守卫）
    ineligibleTip: ''
  },

  onLoad(options) {
    this.setData({ orderNo: options.orderNo || '' });
    this.loadOrder();
  },

  // 加载订单并判定是否可申请（后端仅允许 paid / shipping 订单）
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
      const eligible = canApplyRefund(o.status);
      const maxFen = Number(o.amount) || 0;
      this.setData({
        order: o,
        maxFen: maxFen,
        maxText: formatPrice(maxFen),
        amount: maxFen > 0 ? formatPrice(maxFen) : '',   // 默认全额退款
        eligible: eligible,
        ineligibleTip: eligible ? '' : '当前订单状态（' + (statusText('order', o.status) || o.status) + '）不支持申请退款'
      });
      if (!eligible) {
        wx.showToast({ title: '当前订单状态不支持申请退款', icon: 'none' });
      }
    }).catch((e) => {
      wx.hideLoading();
      wx.showToast({ title: (e && e.msg) || '订单加载失败', icon: 'none' });
      this.setData({ eligible: false, ineligibleTip: '订单加载失败，请下拉重试' });
    });
  },

  onAmount(e) { this.setData({ amount: e.detail.value }); },

  onType(e) { this.setData({ type: e.currentTarget.dataset.type }); },

  // 快捷填入最大可退金额
  onFillAll() {
    if (this.data.maxFen <= 0) return;
    this.setData({ amount: this.data.maxText });
  },

  onReason(e) {
    const v = e.detail.value || '';
    this.setData({ reason: v, reasonLen: v.length });
  },

  // 金额超出可退上限时回填为上限，避免用户反复修改
  onAmountBlur() {
    const fen = yuanToFen(this.data.amount);
    if (fen > this.data.maxFen) {
      wx.showToast({ title: '最多可退 ¥' + this.data.maxText, icon: 'none' });
      this.setData({ amount: this.data.maxText });
    }
  },

  submit() {
    if (this.data.submitting) return;                 // 防重复提交
    if (!this.data.eligible) {
      return wx.showToast({ title: this.data.ineligibleTip || '当前不可申请退款', icon: 'none' });
    }
    const fen = yuanToFen(this.data.amount);
    if (fen < 0) return wx.showToast({ title: '请输入有效的退款金额', icon: 'none' });
    if (fen === 0) return wx.showToast({ title: '退款金额需大于 0', icon: 'none' });
    if (fen > this.data.maxFen) {
      return wx.showToast({ title: '最多可退 ¥' + this.data.maxText, icon: 'none' });
    }
    const reason = (this.data.reason || '').trim();
    if (!reason) return wx.showToast({ title: '请填写退款原因', icon: 'none' });

    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中' });
    api.applyRefund({
      orderNo: this.data.orderNo,
      type: this.data.type,
      amount: fen,
      reason: reason
    }).then((r) => {
      wx.hideLoading();
      const refundNo = r && (r.refundNo || r);
      wx.showToast({ title: '退款申请已提交', icon: 'success' });
      api.track('refund_apply', { order_no: this.data.orderNo, refund_type: this.data.type, amount: fen });
      // 跳详情页继续查看进度（替换当前页，返回时回到订单详情）
      setTimeout(() => {
        this.setData({ submitting: false });
        if (refundNo) {
          wx.redirectTo({ url: '/pages/refund/detail/detail?refundNo=' + refundNo });
        } else {
          wx.navigateBack();
        }
      }, 800);
    }).catch((e) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '申请失败', icon: 'none' });
    });
  }
});
