// pages/recycle/apply/apply.js —— 同城回收：估价 + 预约提交（F-05，对齐 LocalRecycleController）
const api = require('../../../utils/api.js');
const { formatPrice, yuanToFen, conditionText } = require('../../../utils/util.js');

// 常见回收品类（演示用，真实以服务端白名单为准）
const RECYCLE_CATEGORIES = [
  { id: 11, name: '手机数码' },
  { id: 12, name: '电脑平板' },
  { id: 13, name: '家电' },
  { id: 14, name: '相机摄影' },
  { id: 15, name: '智能穿戴' }
];
const CONDITIONS = [
  { level: 1, name: '全新' },
  { level: 2, name: '95新' },
  { level: 3, name: '9成新' },
  { level: 4, name: '8成新' },
  { level: 5, name: '功能完好' }
];

function toast(msg) { wx.showToast({ title: msg || '操作失败', icon: 'none' }); }

Page({
  data: {
    cats: RECYCLE_CATEGORIES,
    conditions: CONDITIONS,
    catIndex: 0,
    condIndex: 2,
    title: '',
    refPriceYuan: '',
    pickupType: 'HOME',
    province: '', city: '', district: '',
    address: '', contactName: '', contactPhone: '',
    appointmentAt: '',
    estimate: null,
    submitting: false
  },

  onCat(e) { this.setData({ catIndex: Number(e.detail.value) }); },
  onCond(e) { this.setData({ condIndex: Number(e.detail.value) }); },
  onPickup(e) { this.setData({ pickupType: e.currentTarget.dataset.type }); },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }); },
  onAppoint(e) { this.setData({ appointmentAt: e.detail.value }); },

  // 预估回收价（不下单，对齐 /api/local/recycle/estimate）
  doEstimate() {
    const cat = this.data.cats[this.data.catIndex];
    const cond = this.data.conditions[this.data.condIndex];
    const refFen = yuanToFen(this.data.refPriceYuan);
    if (!this.data.city.trim()) { toast('请先填写所在城市'); return; }
    api.recycleEstimate(cat.id, cond.level, refFen > 0 ? refFen : null, this.data.city.trim())
      .then((v) => {
        this.setData({
          estimate: Object.assign({}, v, {
            estimateText: formatPrice(v.estimateFen),
            referenceText: formatPrice(v.referenceFen),
            conditionName: conditionText(v.conditionLevel)
          })
        });
      })
      .catch((e) => toast((e && e.msg) || '预估失败'));
  },

  submit() {
    if (this.data.submitting) return;
    const cat = this.data.cats[this.data.catIndex];
    const cond = this.data.conditions[this.data.condIndex];
    const refFen = yuanToFen(this.data.refPriceYuan);
    const city = this.data.city.trim();
    const address = this.data.address.trim();
    const phone = this.data.contactPhone.trim();
    if (!city || !address || !phone) { toast('请填写城市 / 取件地址 / 联系电话'); return; }
    const dto = {
      categoryId: cat.id,
      title: this.data.title.trim() || (cat.name + '回收'),
      conditionLevel: cond.level,
      referencePriceFen: refFen > 0 ? refFen : null,
      pickupType: this.data.pickupType,
      province: this.data.province.trim(),
      city: city,
      district: this.data.district.trim(),
      address: address,
      contactName: this.data.contactName.trim(),
      contactPhone: phone,
      appointmentAt: this.data.appointmentAt || null
    };
    this.setData({ submitting: true });
    wx.showLoading({ title: '提交中' });
    api.recycleApply(dto)
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '回收申请已提交', icon: 'success' });
        setTimeout(() => wx.redirectTo({ url: '/pages/recycle/my/my' }), 800);
      })
      .catch((e) => {
        wx.hideLoading();
        this.setData({ submitting: false });
        toast((e && e.msg) || '提交失败');
      });
  }
});
