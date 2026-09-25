// pages/refund/detail/detail.js —— 退款详情 / 进度（买卖双方共用，按角色 + 状态计算可用操作）
const api = require('../../../utils/api.js');
const { formatPrice, formatTime, statusText, refundStepIndex, isRefundTerminal } = require('../../../utils/util.js');

// 正常推进的三个节点（不接受/撤销等异常流转由顶部 banner 表达）
const STEPS = [
  { title: '卖家处理', desc: '等待卖家处理退款申请' },
  { title: '平台退款', desc: '退款到账处理中' },
  { title: '退款成功', desc: '款项已退回支付账户' }
];

function toast(msg) {
  wx.showToast({ title: msg || '操作失败', icon: 'none' });
}

// 当前登录用户 ID：真实后端返回 userId，mock 返回 id，两者兼容
function currentUserId() {
  const app = getApp();
  const u = app && app.globalData.userInfo;
  if (!u) return 0;
  return Number(u.userId || u.id || 0);
}

Page({
  data: {
    refundNo: '',
    refund: null,
    order: null,
    role: '',            // buyer / seller（按 buyerId/sellerId 与当前用户比对）
    roleText: '',
    stepIndex: -1,
    steps: [],
    terminated: false,   // 已退款/已拒绝/已撤销
    specialText: '',     // 拒绝/撤销/平台介入等提示
    loading: true,
    errorTip: '',
    // 可用操作（按角色 + 后端状态机守卫计算）
    canCancel: false,
    canReturn: false,
    canPlatform: false,
    canAgree: false,
    canConfirmReturn: false,
    canReject: false,
    submitting: false
  },

  onLoad(options) {
    this.setData({ refundNo: options.refundNo || '', role: options.role || '' });
    this.loadDetail();
  },

  onUnload() { this.stopPoll(); },
  onHide() { this.stopPoll(); },
  onShow() { if (this.data.refundNo && !this.data.refund) this.loadDetail(); },

  // 确保已拿到用户信息（用于判定买卖角色）
  ensureUser() {
    const app = getApp();
    if (app.globalData.userInfo) return Promise.resolve(app.globalData.userInfo);
    return api.getUserInfo().then((u) => {
      if (u) app.globalData.userInfo = u;
      return u;
    }).catch(() => null);
  },

  loadDetail() {
    if (!this.data.refundNo) {
      this.setData({ loading: false, errorTip: '缺少退款单号' });
      return toast('缺少退款单号');
    }
    if (!this.data.refund) this.setData({ loading: true });
    return this.ensureUser().then(() => api.getRefund(this.data.refundNo)).then((r) => {
      if (!r) {
        this.setData({ loading: false, errorTip: '退款单不存在' });
        return;
      }
      this.applyRefund(r);
      this.maybePoll(r.status);
      // 商品信息非退款单字段，从订单补充；失败不影响主流程
      return api.getOrder(r.orderNo).then((o) => {
        this.setData({ order: o || null });
      }).catch(() => {});
    }).catch((e) => {
      this.setData({ loading: false, errorTip: (e && e.msg) || '加载失败' });
      toast((e && e.msg) || '加载失败');
    });
  },

  // 由退款单推导展示态与可用操作
  applyRefund(r) {
    const status = r.status;
    const role = this.resolveRole(r);
    const terminated = isRefundTerminal(status);
    const stepIdx = refundStepIndex(status);
    const steps = STEPS.map((s, i) => Object.assign({}, s, {
      state: terminated ? (i <= stepIdx ? 'done' : 'todo') : (i < stepIdx ? 'done' : (i === stepIdx ? 'active' : 'todo'))
    }));
    let specialText = '';
    if (status === 'rejected') specialText = '卖家已拒绝退款申请';
    else if (status === 'canceled') specialText = '退款申请已撤销';
    else if (status === 'platform') specialText = '平台已介入处理，请耐心等待平台裁定';

    this.setData({
      refund: Object.assign({}, r, {
        amountText: formatPrice(r.amount),
        statusT: statusText('refund', status),
        timeT: formatTime(r.createdAt),
        typeT: r.type === 'return_refund' ? '退货退款' : '仅退款'
      }),
      role: role,
      roleText: role === 'buyer' ? '我是买家' : (role === 'seller' ? '我是卖家' : ''),
      stepIndex: stepIdx,
      steps: steps,
      terminated: terminated,
      specialText: specialText,
      loading: false,
      errorTip: '',
      // —— 可用操作（严格对齐后端 RefundService 守卫）——
      // 买家：非终态可撤销；退货退款且待卖家处理且未填物流时可填物流；非终态可申请平台介入
      canCancel: role === 'buyer' && !terminated,
      canReturn: role === 'buyer' && r.type === 'return_refund' && status === 'wait_seller' && !r.logisticsNo,
      canPlatform: role === 'buyer' && !terminated,
      // 卖家：待卖家处理时可同意(仅退款)/确认收货(退货退款)/拒绝
      canAgree: role === 'seller' && status === 'wait_seller' && r.type === 'only_refund',
      canConfirmReturn: role === 'seller' && status === 'wait_seller' && r.type === 'return_refund',
      canReject: role === 'seller' && status === 'wait_seller'
    });
  },

  resolveRole(r) {
    const uid = currentUserId();
    if (uid) {
      if (Number(r.buyerId) === uid) return 'buyer';
      if (Number(r.sellerId) === uid) return 'seller';
    }
    return this.data.role || '';
  },

  // 进行中状态轮询同步（卖家自动同意 48h / 平台裁定 / 另一端操作）
  maybePoll(status) {
    this.stopPoll();
    if (isRefundTerminal(status)) return;
    this.pollTimer = setInterval(() => {
      api.getRefund(this.data.refundNo).then((r) => {
        if (!r) return;
        if (r.status !== (this.data.refund && this.data.refund.status)) {
          this.stopPoll();
          this.applyRefund(r);
          this.maybePoll(r.status);
        }
      }).catch(() => {});
    }, 5000);
  },

  stopPoll() {
    if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; }
  },

  // ===== 操作统一处理：确认弹窗 + loading + 成功刷新 / 失败提示并同步最新状态 =====
  doAction(title, content, fn, successTip) {
    if (this.data.submitting) return;
    wx.showModal({
      title: title,
      content: content,
      success: (c) => {
        if (!c.confirm) return;
        this.setData({ submitting: true });
        wx.showLoading({ title: '处理中' });
        fn().then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: successTip || '操作成功', icon: 'success' });
          api.track('refund_action', { refund_no: this.data.refundNo, action: title });
          this.loadDetail();
        }).catch((e) => {
          wx.hideLoading();
          this.setData({ submitting: false });
          // 多为状态守卫拦截（如"当前状态不可同意"），同步最新状态避免页面停留在旧态
          toast((e && e.msg) || '操作失败');
          this.loadDetail();
        });
      }
    });
  },

  // —— 买家操作 ——
  cancelRefund() {
    this.doAction('撤销退款', '撤销后将无法恢复，确定撤销该退款申请吗？',
      () => api.cancelRefund(this.data.refundNo), '已撤销退款');
  },

  askPlatform() {
    this.doAction('申请平台介入', '平台将在 1-3 个工作日内裁定，确定申请介入吗？',
      () => api.platformRefund(this.data.refundNo), '已提交平台介入');
  },

  // 退货退款：买家填写退货物流单号
  returnLogistics() {
    if (this.data.submitting) return;
    wx.showModal({
      title: '填写退货物流',
      editable: true,
      placeholderText: '请输入退货快递单号',
      success: (c) => {
        if (!c.confirm) return;
        const no = (c.content || '').trim();
        if (!no) return toast('请输入退货快递单号');
        this.setData({ submitting: true });
        wx.showLoading({ title: '提交中' });
        api.returnRefundLogistics(this.data.refundNo, no).then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: '已提交物流单号', icon: 'success' });
          this.loadDetail();
        }).catch((e) => {
          wx.hideLoading();
          this.setData({ submitting: false });
          toast((e && e.msg) || '提交失败');
          this.loadDetail();
        });
      }
    });
  },

  // —— 卖家操作 ——
  agreeRefund() {
    this.doAction('同意退款', '同意后将立即为您办理退款，金额 ¥' + (this.data.refund && this.data.refund.amountText),
      () => api.agreeRefund(this.data.refundNo), '已同意退款');
  },

  confirmReturn() {
    this.doAction('确认收货并退款', '请确认已收到买家退回的商品且无误，确认后将退款给买家',
      () => api.confirmRefundReturn(this.data.refundNo), '已退款');
  },

  rejectRefund() {
    if (this.data.submitting) return;
    wx.showModal({
      title: '拒绝退款',
      editable: true,
      placeholderText: '请输入拒绝原因（买家可见）',
      success: (c) => {
        if (!c.confirm) return;
        const reason = (c.content || '').trim();
        if (!reason) return toast('请输入拒绝原因');
        this.setData({ submitting: true });
        wx.showLoading({ title: '处理中' });
        api.rejectRefund(this.data.refundNo, reason).then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: '已拒绝退款', icon: 'success' });
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

  goOrder() {
    const r = this.data.refund;
    if (r && r.orderNo) wx.redirectTo({ url: '/pages/order/detail/detail?orderNo=' + r.orderNo });
  },

  contact() {
    const r = this.data.refund;
    const o = this.data.order;
    if (!r) return;
    const peerId = this.data.role === 'buyer' ? r.sellerId : r.buyerId;
    const itemId = (o && o.item && o.item.id) || '';
    const itemTitle = (o && o.item && o.item.title) || '';
    wx.navigateTo({
      url: '/pages/chat/chat?convId=C' + r.orderNo + '&peerId=' + peerId + '&itemId=' + itemId + '&itemTitle=' + encodeURIComponent(itemTitle)
    });
  }
});
