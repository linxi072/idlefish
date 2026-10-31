// pages/delivery/apply/apply.js —— 同城配送：可用性预检 + 下单（F-05，对齐 LocalDeliveryController）
const api = require('../../../utils/api.js');
const { formatPrice, deliveryBizText } = require('../../../utils/util.js');

function toast(msg) { wx.showToast({ title: msg || '操作失败', icon: 'none' }); }

Page({
  data: {
    bizType: 'ORDER',
    bizNo: '',
    city: '',
    fromAddress: '',
    toAddress: '',
    distanceKm: '',
    pickupContact: '',
    receiverContact: '',
    remark: '',
    check: null,            // { available, freightText, reason, distanceOk }
    submitting: false
  },

  onBiz(e) { this.setData({ bizType: e.currentTarget.dataset.type, check: null }); },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value, check: null }); },

  // 预检同城可用性 + 运费（对齐 /api/local/delivery/check）
  doCheck() {
    const city = this.data.city.trim();
    if (!city) { toast('请填写配送城市'); return; }
    const dist = this.data.distanceKm ? Number(this.data.distanceKm) : null;
    api.deliveryCheck(city, dist).then((v) => {
      this.setData({
        check: Object.assign({}, v, { freightText: formatPrice(v.freightFen || 0) })
      });
    }).catch((e) => toast((e && e.msg) || '预检失败'));
  },

  submit() {
    if (this.data.submitting) return;
    const city = this.data.city.trim();
    const from = this.data.fromAddress.trim();
    const to = this.data.toAddress.trim();
    const pickup = this.data.pickupContact.trim();
    const receiver = this.data.receiverContact.trim();
    if (!city || !from || !to || !pickup || !receiver) { toast('请填写城市/起终点/取件与收货联系人'); return; }
    const dto = {
      bizType: this.data.bizType,
      bizNo: this.data.bizNo.trim() || ('M' + Date.now()),
      city: city,
      fromAddress: from,
      toAddress: to,
      distanceKm: this.data.distanceKm ? Number(this.data.distanceKm) : null,
      pickupContact: pickup,
      receiverContact: receiver,
      remark: this.data.remark.trim()
    };
    this.setData({ submitting: true });
    wx.showLoading({ title: '下单中' });
    api.deliveryApply(dto).then(() => {
      wx.hideLoading();
      wx.showToast({ title: '同城配送已下单', icon: 'success' });
      setTimeout(() => wx.redirectTo({ url: '/pages/delivery/my/my' }), 800);
    }).catch((e) => {
      wx.hideLoading();
      this.setData({ submitting: false });
      toast((e && e.msg) || '下单失败');
    });
  }
});
