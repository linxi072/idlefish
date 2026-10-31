// pages/delivery/detail/detail.js —— 同城配送详情 / 轨迹时间轴 / 取消（F-05，对齐 LocalDeliveryController）
const api = require('../../../utils/api.js');
const { formatPrice, formatTime, deliveryStatusText, deliveryBizText } = require('../../../utils/util.js');

function toast(msg) { wx.showToast({ title: msg || '操作失败', icon: 'none' }); }

// 轨迹节点类型 → 着色类
function trackClass(type) {
  if (type === 'sign') return 't-sign';
  if (type === 'exception') return 't-exception';
  if (type === 'transit' || type === 'transport') return 't-transit';
  return 't-unknown';
}

Page({
  data: {
    deliveryNo: '',
    delivery: null,
    tracks: [],
    loading: true,
    errorTip: '',
    canCancel: false,
    submitting: false
  },

  onLoad(options) {
    this.setData({ deliveryNo: options.deliveryNo || '' });
    this.loadDetail();
  },

  onShow() { if (this.data.deliveryNo && !this.data.delivery) this.loadDetail(); },

  loadDetail() {
    if (!this.data.deliveryNo) { this.setData({ loading: false, errorTip: '缺少配送单号' }); return; }
    if (!this.data.delivery) this.setData({ loading: true });
    api.deliveryDetail(this.data.deliveryNo).then((r) => {
      if (!r || !r.delivery) { this.setData({ loading: false, errorTip: '配送单不存在' }); return; }
      const o = r.delivery;
      const tracks = (r.tracks || []).map((t) => Object.assign({}, t, { cls: trackClass(t.type) }));
      this.setData({
        delivery: Object.assign({}, o, {
          statusText: deliveryStatusText(o.status),
          bizText: deliveryBizText(o.bizType),
          freightText: formatPrice(o.freight || 0),
          createdText: formatTime(o.createdAt),
          acceptedText: formatTime(o.acceptedAt),
          deliveredText: formatTime(o.deliveredAt)
        }),
        tracks,
        loading: false,
        errorTip: '',
        canCancel: (o.status === 'CREATED' || o.status === 'ACCEPTED' || o.status === 'DELIVERING')
      });
    }).catch((e) => {
      this.setData({ loading: false, errorTip: (e && e.msg) || '加载失败' });
      toast((e && e.msg) || '加载失败');
    });
  },

  cancelDelivery() {
    if (this.data.submitting) return;
    wx.showModal({
      title: '取消配送', content: '未送达前可取消配送单，确定取消吗？',
      success: (c) => {
        if (!c.confirm) return;
        this.setData({ submitting: true });
        wx.showLoading({ title: '处理中' });
        api.deliveryCancel(this.data.deliveryNo).then(() => {
          wx.hideLoading();
          this.setData({ submitting: false });
          wx.showToast({ title: '已取消配送', icon: 'success' });
          this.loadDetail();
        }).catch((e) => {
          wx.hideLoading();
          this.setData({ submitting: false });
          toast((e && e.msg) || '取消失败');
          this.loadDetail();
        });
      }
    });
  },

  goApply() { wx.navigateTo({ url: '/pages/delivery/apply/apply' }); }
});
