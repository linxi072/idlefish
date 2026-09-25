const api = require('../../utils/api.js');
const { formatPrice, couponTypeText, couponDesc, couponScopeText } = require('../../utils/util.js');

Page({
  data: {
    itemId: null, item: null, loading: true,
    addresses: [], address: null,
    coupons: [],              // 下单可用券（含 discountAmount，已按抵扣降序）
    selectedCouponId: 0,      // 0 = 不使用优惠券
    goodsAmount: 0,           // 商品金额（分）
    discountAmount: 0,        // 抵扣（分）
    freight: 0,               // 运费（分）
    payAmount: 0,             // 实付（分）
    discountText: '-¥0.00',
    payText: '0.00',
    submitting: false
  },

  onLoad(options) {
    const itemId = Number(options.itemId);
    this.setData({ itemId });
    this.loadItem(itemId);
    this.loadAddresses();
  },

  loadItem(itemId) {
    api.getItemDetail(itemId).then((it) => {
      it.priceText = formatPrice(it.price || 0);   // 金额单位：分（契约）
      const goodsAmount = it.price || 0;
      this.setData({ item: it, goodsAmount, loading: false }, () => this.loadCoupons());
    }).catch((e) => {
      this.setData({ loading: false });
      wx.showToast({ title: (e && e.msg) || '商品加载失败', icon: 'none' });
    });
  },

  loadAddresses() {
    api.getAddresses().then((list) => {
      const arr = list || [];
      const addr = arr.find(a => a.isDefault) || arr[0] || null;
      this.setData({ addresses: arr, address: addr });
    }).catch(() => {});
  },

  // 拉取下单可用券，默认选中抵扣最大的一张
  loadCoupons() {
    const { itemId, goodsAmount } = this.data;
    api.couponAvailable(itemId, goodsAmount).then((list) => {
      const coupons = (list || []).map(c => Object.assign({}, c, {
        typeText: couponTypeText(c.type),
        desc: couponDesc(c),
        scopeText: couponScopeText(c.scope),
        discountText: '-¥' + formatPrice(c.discountAmount || 0)
      }));
      const best = coupons.length ? coupons[0].id : 0;   // 后端已按 discountAmount 降序
      this.setData({ coupons }, () => this.selectCoupon(best));
    }).catch(() => { this.setData({ coupons: [] }); });
  },

  chooseAddress() {
    const list = this.data.addresses;
    if (!list.length) { wx.navigateTo({ url: '/pages/address/address' }); return; }
    wx.showActionSheet({
      itemList: list.map(a => a.receiver + ' ' + a.phone + ' ' + a.province + a.city + a.district + a.detail),
      success: (r) => this.setData({ address: list[r.tapIndex] })
    });
  },

  selectCoupon(e) {
    const id = e ? Number(e.currentTarget.dataset.id) : 0;
    const { coupons, goodsAmount, freight } = this.data;
    const sel = coupons.find(c => c.id === id);
    const discount = sel ? (sel.discountAmount || 0) : 0;
    const payAmount = Math.max(0, goodsAmount - discount + freight);
    this.setData({
      selectedCouponId: id, discountAmount: discount, payAmount,
      discountText: '-¥' + formatPrice(discount),
      payText: formatPrice(payAmount)
    });
  },

  onSubmit() {
    if (this.data.submitting) return;
    const { itemId, address, selectedCouponId, goodsAmount } = this.data;
    if (!address) { wx.showToast({ title: '请选择收货地址', icon: 'none' }); return; }
    this.setData({ submitting: true });
    wx.showLoading({ title: '提交订单' });
    api.createOrder({
      itemId, quantity: 1, addressId: address.id,
      userCouponId: selectedCouponId || null,
      amount: goodsAmount,
      idempotentKey: 'k_' + itemId + '_' + Date.now()
    }).then((order) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      api.track('order_create', { order_no: order.orderNo, item_id: itemId, amount: order.amount, discount: order.discountAmount });
      wx.redirectTo({ url: '/pages/order/detail/detail?orderNo=' + order.orderNo });
    }).catch((e) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '下单失败', icon: 'none' });
    });
  }
});
