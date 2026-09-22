const api = require('../../utils/api.js');
const { formatPrice, statusText, formatTime } = require('../../utils/util.js');

Page({
  data: {
    orderNo: '', order: null, amountText: '', statusT: '', timeT: '',
    showShip: false, showRefund: false,
    shipForm: { company: '', logisticNo: '' },
    refundForm: { type: 'only_refund', reason: '' }
  },

  onLoad(options) {
    this.setData({ orderNo: options.orderNo });
    this.reload();
  },

  onUnload() { this.stopPoll(); },

  fmt(o) {
    o.amountText = formatPrice(o.amount);
    o.statusT = statusText('order', o.status);
    o.timeT = formatTime(o.createdAt);
    return o;
  },

  reload() {
    api.getOrder(this.data.orderNo).then((o) => {
      this.fmt(o);
      this.setData({ order: o });
      this.maybePoll(o.status);
    }).catch((e) => wx.showToast({ title: (e && e.msg) || '加载失败', icon: 'none' }));
  },

  // R-11：待支付状态下轮询订单状态，支付成功后自动刷新
  maybePoll(status) {
    this.stopPoll();
    if (status === 'pending_pay') {
      this.pollTimer = setInterval(() => {
        api.getOrder(this.data.orderNo).then((o) => {
          if (o.status !== 'pending_pay') {
            this.stopPoll();
            this.reload();
          } else {
            this.setData({ order: this.fmt(o) });
          }
        }).catch(() => {});
      }, 3000);
    }
  },

  stopPoll() { if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; } },

  pay() {
    const no = this.data.orderNo;
    wx.showLoading({ title: '发起支付' });
    api.prepay({ orderNo: no, payChannel: 'wechat' }).then((r) => {
      if (getApp().globalData.useMock) {
        // Mock 模式：直接触发后端 mock 完成，走通 待支付→已支付
        return api.payMockComplete(r.payNo).then(() => {
          wx.hideLoading();
          api.track('pay_success', { order_no: no, pay_no: r.payNo });
          wx.showToast({ title: '支付成功', icon: 'success' });
          this.reload();
        });
      }
      return new Promise((resolve, reject) => {
        wx.requestPayment(Object.assign({ success: () => resolve(), fail: (err) => reject(err) }, r.prepayParams));
      }).then(() => api.payNotify({ orderNo: no, payNo: r.payNo, status: 'paid' }))
        .then(() => { wx.hideLoading(); this.reload(); })
        .catch(() => { wx.hideLoading(); wx.showToast({ title: '支付取消', icon: 'none' }); });
    }).catch((e) => { wx.hideLoading(); wx.showToast({ title: (e && e.msg) || '支付失败', icon: 'none' }); });
  },

  confirm() {
    wx.showModal({ title: '确认收货', content: '确认后平台将把货款结算给卖家', success: (r) => {
      if (r.confirm) api.confirmOrder(this.data.orderNo).then(() => {
        api.track('order_confirm', { order_no: this.data.orderNo });
        wx.showToast({ title: '已确认收货', icon: 'success' }); this.reload();
      }).catch((e) => wx.showToast({ title: (e && e.msg) || '操作失败', icon: 'none' }));
    } });
  },

  openShip() { this.setData({ showShip: true }); },
  closeShip() { this.setData({ showShip: false }); },
  onShipField(e) { this.setData({ ['shipForm.' + e.currentTarget.dataset.k]: e.detail.value }); },
  submitShip() {
    const f = this.data.shipForm;
    if (!f.company.trim() || !f.logisticNo.trim()) return wx.showToast({ title: '请填写物流公司与单号', icon: 'none' });
    api.shipOrder(this.data.orderNo, f).then(() => {
      this.setData({ showShip: false });
      wx.showToast({ title: '已发货', icon: 'success' });
      api.track('logistics_ship', { order_no: this.data.orderNo, company: f.company, logistic_no: f.logisticNo });
      this.reload();
    }).catch((e) => wx.showToast({ title: (e && e.msg) || '发货失败', icon: 'none' }));
  },

  openRefund() { this.setData({ showRefund: true }); },
  closeRefund() { this.setData({ showRefund: false }); },
  onRefundField(e) { this.setData({ ['refundForm.' + e.currentTarget.dataset.k]: e.detail.value }); },
  onRefundType(e) { this.setData({ 'refundForm.type': e.currentTarget.dataset.type }); },
  submitRefund() {
    const f = this.data.refundForm;
    if (!f.reason.trim()) return wx.showToast({ title: '请填写退款原因', icon: 'none' });
    api.applyRefund({ orderNo: this.data.orderNo, type: f.type, reason: f.reason, amount: this.data.order.amount }).then((r) => {
      this.setData({ showRefund: false });
      wx.showToast({ title: '退款申请已提交', icon: 'success' });
      api.track('refund_apply', { order_no: this.data.orderNo, refund_type: f.type, amount: this.data.order.amount });
      this.reload();
    }).catch((e) => wx.showToast({ title: (e && e.msg) || '申请失败', icon: 'none' }));
  },

  contact() {
    const o = this.data.order;
    const peer = o.role === 'buyer' ? o.item.seller : { id: o.buyerId };
    wx.navigateTo({ url: `/pages/chat/chat?convId=C${o.orderNo}&peerId=${peer.id}&itemId=${o.item.id}&itemTitle=${o.item.title}` });
  },
  goItem() { wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + this.data.order.item.id }); }
});
