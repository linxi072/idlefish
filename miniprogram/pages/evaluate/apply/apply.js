// pages/evaluate/apply/apply.js —— 提交评价（买家评卖家 / 卖家评买家）
const api = require('../../../utils/api.js');
const { formatPrice, canEvaluate, orderRoleToReviewRole, reviewRoleLabel, ratingArray, statusText } = require('../../../utils/util.js');

const CONTENT_MAX = 512;

Page({
  data: {
    orderNo: '',
    order: null,
    roleCode: '',        // 我的评价角色：BUYER_SELLER / SELLER_BUYER
    roleLabel: '',
    rating: 5,
    ratingArr: [true, true, true, true, true],
    content: '',
    contentLen: 0,
    contentMax: CONTENT_MAX,
    anonymous: false,
    existing: null,      // 已评价（只读展示，后端 my 仅返回通过的评价）
    submitting: false,
    eligible: false,     // 订单是否允许评价（对齐后端 ReviewService.submit 守卫）
    ineligibleTip: ''
  },

  onLoad(options) {
    this.setData({ orderNo: options.orderNo || '' });
    this.loadOrder();
  },

  // 加载订单并判定是否可评价；已评价则只读展示
  loadOrder() {
    if (!this.data.orderNo) {
      this.setData({ eligible: false, ineligibleTip: '缺少订单号' });
      return;
    }
    wx.showLoading({ title: '加载中' });
    api.getOrder(this.data.orderNo).then((o) => {
      if (!o) {
        wx.hideLoading();
        this.setData({ eligible: false, ineligibleTip: '订单不存在' });
        return;
      }
      const eligible = canEvaluate(o.status);
      const roleCode = orderRoleToReviewRole(o.role);
      this.setData({
        order: o, roleCode, roleLabel: reviewRoleLabel(roleCode),
        eligible, ineligibleTip: eligible ? '' : '当前订单状态（' + (statusText('order', o.status) || o.status) + '）不可评价'
      });
      // 判定是否已评价：同订单、同角色存在通过的评价即只读展示
      return api.myReviews().then((list) => {
        wx.hideLoading();
        const ex = (list || []).find(r => r.orderNo === this.data.orderNo && r.role === roleCode);
        if (ex) {
          ex.ratingArr = ratingArray(ex.rating);
          this.setData({ existing: ex });
        }
        if (!eligible) wx.showToast({ title: '当前订单状态不可评价', icon: 'none' });
      });
    }).catch((e) => {
      wx.hideLoading();
      wx.showToast({ title: (e && e.msg) || '订单加载失败', icon: 'none' });
      this.setData({ eligible: false, ineligibleTip: '订单加载失败，请下拉重试' });
    });
  },

  onRating(e) {
    if (this.data.existing) return;
    const n = Number(e.currentTarget.dataset.n);
    this.setData({ rating: n, ratingArr: ratingArray(n) });
  },

  onContent(e) {
    if (this.data.existing) return;
    const v = e.detail.value || '';
    this.setData({ content: v, contentLen: v.length });
  },

  onAnon(e) {
    if (this.data.existing) return;
    this.setData({ anonymous: !!e.detail.value });
  },

  submit() {
    if (this.data.submitting) return;
    if (this.data.existing) return wx.showToast({ title: '您已评价，无需重复提交', icon: 'none' });
    if (!this.data.eligible) return wx.showToast({ title: this.data.ineligibleTip || '当前不可评价', icon: 'none' });
    const content = (this.data.content || '').trim();
    if (!content) return wx.showToast({ title: '请填写评价内容', icon: 'none' });
    if (content.length > this.data.contentMax) return wx.showToast({ title: '评价内容过长', icon: 'none' });

    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中' });
    api.submitReview({
      orderNo: this.data.orderNo,
      rating: this.data.rating,
      content: content,
      anonymous: this.data.anonymous ? 1 : 0
    }).then(() => {
      wx.hideLoading();
      wx.showToast({ title: '评价已提交', icon: 'success' });
      api.track('review_submit', { order_no: this.data.orderNo, rating: this.data.rating, anonymous: this.data.anonymous ? 1 : 0 });
      // 返回订单详情，onShow 会刷新「已评价」状态
      setTimeout(() => { this.setData({ submitting: false }); wx.navigateBack(); }, 800);
    }).catch((e) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '提交失败', icon: 'none' });
    });
  },

  goOrder() { wx.navigateBack(); }
});
