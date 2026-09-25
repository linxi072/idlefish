const api = require('../../utils/api.js');
const { formatPrice, couponTypeText, couponDesc, couponScopeText, couponStatusText, formatTime } = require('../../utils/util.js');

const TABS = [
  { key: 'UNUSED', label: '未使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' }
];

Page({
  data: { tabs: TABS, active: 'UNUSED', list: [], loading: true, loadErr: false },

  onShow() { this.load(this.data.active); },

  switchTab(e) {
    const key = e.currentTarget.dataset.key;
    if (key === this.data.active) return;
    this.setData({ active: key }, () => this.load(key));
  },

  load(status) {
    this.setData({ loading: true, loadErr: false });
    api.couponMy(status).then((list) => {
      const arr = (list || []).map(c => Object.assign({}, c, {
        typeText: couponTypeText(c.type),
        desc: couponDesc(c),
        scopeText: couponScopeText(c.scope),
        statusText: couponStatusText(c.status),
        expireText: c.expireAt ? formatTime(c.expireAt) : '',
        discountText: c.discountAmount ? '-¥' + formatPrice(c.discountAmount) : ''
      }));
      this.setData({ list: arr, loading: false });
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  retry() { this.load(this.data.active); },

  goCenter() { wx.navigateTo({ url: '/pages/coupon/center/center' }); }
});
