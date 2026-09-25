const api = require('../../utils/api.js');
const { formatPrice, fromNow, statusText, ratingArray } = require('../../utils/util.js');

Page({
  data: {
    id: null, item: null, loading: true, fav: false, curImg: 0,
    // 评价展示（对齐后端 /api/reviews/item/:id，仅通过）
    reviews: [], reviewCount: 0, reviewAvg: 0, reviewStars: [false, false, false, false, false]
  },

  onLoad(options) {
    const id = options.id;
    this.setData({ id });
    api.getItemDetail(id).then((item) => {
      item.priceText = formatPrice(item.price);
      item.originalText = item.originalPrice ? formatPrice(item.originalPrice) : '';
      item.statusT = statusText('item', item.status);
      this.setData({ item, loading: false });
      api.track('item_view', { item_id: id, source: 'detail' });
      api.favoriteCheck(id).then((fav) => this.setData({ fav: !!fav })).catch(() => {});
      this.loadReviews(id);
    }).catch((e) => {
      this.setData({ loading: false });
      wx.showToast({ title: (e && e.msg) || '加载失败', icon: 'none' });
    });
  },

  onImgChange(e) { this.setData({ curImg: e.detail.current }); },

  // 拉取商品评价（通过），计算均分与星标，补充展示名（匿名→匿名用户）
  loadReviews(id) {
    api.listReviewsByItem(id).then((list) => {
      const arr = (list || []).map(r => Object.assign({}, r, {
        ratingArr: ratingArray(r.rating),
        peerName: r.anonymous ? '匿名用户' : ('用户' + r.reviewerId)
      }));
      const count = arr.length;
      const avg = count ? Math.round(arr.reduce((s, r) => s + r.rating, 0) / count * 10) / 10 : 0;
      this.setData({ reviews: arr, reviewCount: count, reviewAvg: avg, reviewStars: ratingArray(Math.round(avg)) });
    }).catch(() => {});
  },

  toggleFav() {
    const itemId = this.data.id;
    const willFav = !this.data.fav;
    this.setData({ fav: willFav });
    const p = willFav ? api.favoriteAdd(itemId) : api.favoriteRemove(itemId);
    p.then(() => wx.showToast({ title: willFav ? '已收藏' : '已取消', icon: 'none' }))
      .catch((e) => {
        this.setData({ fav: !willFav });
        wx.showToast({ title: (e && e.msg) || '操作失败', icon: 'none' });
      });
  },

  contactSeller() {
    const it = this.data.item;
    wx.navigateTo({ url: `/pages/chat/chat?convId=C${it.id}&peerId=${it.seller.id}&itemId=${it.id}&itemTitle=${encodeURIComponent(it.title)}&itemImg=${it.cover}&itemPrice=${it.priceText || it.price}` });
  },

  buyNow() {
    const it = this.data.item;
    if (it.status !== 'onsale') { wx.showToast({ title: '商品当前不可购买', icon: 'none' }); return; }
    wx.showLoading({ title: '创建订单' });
    api.getAddresses().then((addrs) => {
      const addr = (addrs || []).find(a => a.isDefault) || (addrs || [])[0];
      if (!addr) {
        wx.hideLoading();
        wx.showModal({ title: '提示', content: '请先添加收货地址', confirmText: '去添加', success: (r) => { if (r.confirm) wx.navigateTo({ url: '/pages/address/address' }); } });
        return Promise.reject({ silent: true });
      }
      return api.createOrder({
        itemId: it.id, quantity: 1, addressId: addr.id,
        idempotentKey: 'k_' + it.id + '_' + Date.now()
      });
    }).then((order) => {
      wx.hideLoading();
      api.track('order_create', { order_no: order.orderNo, item_id: it.id, amount: order.amount });
      wx.navigateTo({ url: '/pages/order/detail/detail?orderNo=' + order.orderNo });
    }).catch((e) => {
      if (!e || !e.silent) { wx.hideLoading(); wx.showToast({ title: (e && e.msg) || '下单失败', icon: 'none' }); }
    });
  },

  onShareAppMessage() {
    const it = this.data.item || {};
    return { title: it.title || '闲置好物', path: '/pages/item-detail/item-detail?id=' + this.data.id };
  }
});
