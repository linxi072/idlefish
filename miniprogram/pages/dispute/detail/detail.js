// pages/dispute/detail/detail.js —— 维权工单详情（买卖双方共用，按角色 + 状态计算可用操作）
const api = require('../../../utils/api.js');
const { formatPrice, formatTime, disputeStatusText, disputeTypeText, disputeExpectText, disputeResultText, isDisputeTerminal } = require('../../../utils/util.js');

function toast(msg) { wx.showToast({ title: msg || '操作失败', icon: 'none' }); }

// 当前登录用户 ID（真实后端返回 userId，mock 返回 id，两者兼容）
function currentUserId() {
  const app = getApp();
  const u = app && app.globalData.userInfo;
  if (!u) return 0;
  return Number(u.userId || u.id || 0);
}

Page({
  data: {
    id: '',
    dispute: null,
    role: '',            // buyer / seller
    roleText: '',
    terminated: false,
    specialText: '',
    loading: true,
    errorTip: '',
    canCancel: false,    // 买家撤销（平台介入后不可）
    canApplyPlatform: false, // 买卖均可申请平台介入
    canSellerReply: false,   // 卖家举证
    submitting: false
  },

  onLoad(options) {
    this.setData({ id: options.id || '' });
    this.loadDetail();
  },
  onShow() { if (this.data.id && !this.data.dispute) this.loadDetail(); },

  ensureUser() {
    const app = getApp();
    if (app.globalData.userInfo) return Promise.resolve(app.globalData.userInfo);
    return api.getUserInfo().then((u) => { if (u) app.globalData.userInfo = u; return u; }).catch(() => null);
  },

  loadDetail() {
    if (!this.data.id) {
      this.setData({ loading: false, errorTip: '缺少工单号' });
      return toast('缺少工单号');
    }
    if (!this.data.dispute) this.setData({ loading: true });
    return this.ensureUser().then(() => api.disputeDetail(Number(this.data.id))).then((d) => {
      if (!d) { this.setData({ loading: false, errorTip: '工单不存在' }); return; }
      this.applyDispute(d);
    }).catch((e) => {
      this.setData({ loading: false, errorTip: (e && e.msg) || '加载失败' });
      toast((e && e.msg) || '加载失败');
    });
  },

  applyDispute(d) {
    const role = this.resolveRole(d);
    const terminated = isDisputeTerminal(d.status);
    let specialText = '';
    if (d.status === 'CANCELED') specialText = '维权已撤销';
    else if (d.status === 'PLATFORM') specialText = '平台已介入，请等待裁定';
    else if (d.status === 'RESOLVED') specialText = '平台已裁决，等待退款执行';
    else if (d.status === 'SELLER_REPLIED') specialText = '卖家已举证，可申请平台介入';

    this.setData({
      dispute: Object.assign({}, d, {
        amountText: formatPrice(d.amount),
        statusT: disputeStatusText(d.status),
        typeT: disputeTypeText(d.type),
        expectT: disputeExpectText(d.expectation),
        resultT: d.result ? disputeResultText(d.result) : '',
        refundText: formatPrice(d.refundAmount),
        timeT: formatTime(d.createdAt)
      }),
      role: role,
      roleText: role === 'buyer' ? '我是买家' : (role === 'seller' ? '我是卖家' : ''),
      terminated: terminated,
      specialText: specialText,
      loading: false,
      errorTip: '',
      canCancel: role === 'buyer' && (d.status === 'PENDING' || d.status === 'SELLER_REPLIED'),
      canApplyPlatform: (role === 'buyer' || role === 'seller') && (d.status === 'PENDING' || d.status === 'SELLER_REPLIED'),
      canSellerReply: role === 'seller' && d.status === 'PENDING'
    });
  },

  resolveRole(d) {
    const uid = currentUserId();
    if (uid) {
      if (Number(d.buyerId) === uid) return 'buyer';
      if (Number(d.sellerId) === uid) return 'seller';
    }
    return this.data.role || '';
  },

  doAction(title, content, fn, successTip) {
    if (this.data.submitting) return;
    wx.showModal({
      title: title, content: content,
      success: (c) => {
        if (!c.confirm) return;
        this.setData({ submitting: true });
        wx.showLoading({ title: '处理中' });
        fn().then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: successTip || '操作成功', icon: 'success' });
          api.track('dispute_action', { dispute_id: this.data.id, action: title });
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

  cancelDispute() {
    this.doAction('撤销维权', '撤销后将无法恢复，确定撤销该维权吗？',
      () => api.disputeCancel(Number(this.data.id)), '已撤销维权');
  },

  applyPlatform() {
    this.doAction('申请平台介入', '平台将在 1-3 个工作日内裁定，确定申请介入吗？',
      () => api.disputeApplyPlatform(Number(this.data.id)), '已提交平台介入');
  },

  sellerReply() {
    if (this.data.submitting) return;
    wx.showModal({
      title: '卖家举证', editable: true, placeholderText: '请输入举证说明（如物流/质检凭证）',
      success: (c) => {
        if (!c.confirm) return;
        const evidence = (c.content || '').trim();
        if (!evidence) return toast('请填写举证说明');
        this.setData({ submitting: true });
        wx.showLoading({ title: '提交中' });
        api.disputeSellerReply(Number(this.data.id), evidence).then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: '已提交举证', icon: 'success' });
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

  goOrder() {
    const d = this.data.dispute;
    if (d && d.orderNo) wx.redirectTo({ url: '/pages/order/detail/detail?orderNo=' + d.orderNo });
  },

  contact() {
    const d = this.data.dispute;
    if (!d) return;
    const peerId = this.data.role === 'buyer' ? d.sellerId : d.buyerId;
    wx.navigateTo({ url: '/pages/chat/chat?convId=C' + d.orderNo + '&peerId=' + peerId + '&itemId=' + '&itemTitle=' });
  }
});
