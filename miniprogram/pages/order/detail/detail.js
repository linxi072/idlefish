const api = require('../../utils/api.js');
const { formatPrice, statusText, formatTime, canApplyRefund, canEvaluate, orderRoleToReviewRole } = require('../../utils/util.js');

Page({
  data: {
    orderNo: '', order: null, amountText: '', statusT: '', timeT: '',
    showShip: false, shipForm: { company: '', logisticNo: '' },
    // 售后：该订单已有的退款单（用于「查看/处理退款」入口）+ 是否可发起申请
    refund: null, canApply: false,
    // 评价：仅已完成/已关闭订单可评；已评价则入口显示「查看评价」
    reviewed: false, canEvaluate: false
  },

  onLoad(options) {
    this.setData({ orderNo: options.orderNo });
    this.reload();
  },

  onShow() {
    // 从退款页返回后刷新最新售后状态
    if (this.data.orderNo) this.reload();
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
      this.loadRefund();
      this.loadReviewState(o);
    }).catch((e) => wx.showToast({ title: (e && e.msg) || '加载失败', icon: 'none' }));
  },

  // 加载该订单的售后单：后端按订单维度查询，取进行中（若无则最新一条）作为入口
  loadRefund() {
    api.listRefundsByOrder(this.data.orderNo).then((list) => {
      const arr = list || [];
      const ongoing = ['apply', 'wait_seller', 'platform', 'refunding'];
      const pick = arr.find(r => ongoing.indexOf(r.status) >= 0) || arr[arr.length - 1] || null;
      this.setData({ refund: pick || null });
      this.refreshApplyFlag();
    }).catch(() => {
      // 售后信息加载失败不影响订单主流程
      this.refreshApplyFlag();
    });
  },

  // 是否可发起退款申请：无进行中退款单 且 订单状态符合后端守卫（paid / shipping）
  refreshApplyFlag() {
    const st = this.data.order && this.data.order.status;
    this.setData({ canApply: !this.data.refund && canApplyRefund(st) });
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

  // ===== 售后入口（统一走 pages/refund/* 页面，不再内联弹窗）=====
  goRefundApply() {
    if (!this.data.canApply) {
      return wx.showToast({ title: '当前订单状态不支持申请退款', icon: 'none' });
    }
    wx.navigateTo({ url: '/pages/refund/apply/apply?orderNo=' + this.data.orderNo });
  },

  goRefundDetail() {
    const r = this.data.refund;
    if (!r) return;
    const role = (this.data.order && this.data.order.role) || '';
    wx.navigateTo({ url: '/pages/refund/detail/detail?refundNo=' + r.refundNo + '&role=' + role });
  },

  // 评价入口状态：仅已完成/已关闭订单可评；调 myReviews 判定该订单该角色是否已评
  loadReviewState(o) {
    if (!canEvaluate(o.status)) {
      this.setData({ canEvaluate: false, reviewed: false });
      return;
    }
    const roleCode = orderRoleToReviewRole(o.role);
    api.myReviews().then((list) => {
      const reviewed = (list || []).some(r => r.orderNo === o.orderNo && r.role === roleCode);
      this.setData({ canEvaluate: true, reviewed });
    }).catch(() => { this.setData({ canEvaluate: true, reviewed: false }); });
  },

  goEvaluate() {
    wx.navigateTo({ url: '/pages/evaluate/apply/apply?orderNo=' + this.data.orderNo });
  },

  contact() {
    const o = this.data.order;
    const peer = o.role === 'buyer' ? o.item.seller : { id: o.buyerId };
    wx.navigateTo({ url: `/pages/chat/chat?convId=C${o.orderNo}&peerId=${peer.id}&itemId=${o.item.id}&itemTitle=${o.item.title}` });
  },
  goItem() { wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + this.data.order.item.id }); }
});
