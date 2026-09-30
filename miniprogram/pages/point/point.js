const api = require('../../utils/api.js');
const { formatPrice, yuanToFen } = require('../../utils/util.js');

// 积分变动业务类型中文（对齐后端 PointLogVO.bizType）
const POINT_BIZ = {
  EARN_SIGNIN: '签到奖励', EARN_TRADE: '交易奖励', EARN_REVIEW: '评价奖励',
  REDEEM: '积分抵现', REDEEM_RELEASED: '抵扣释放'
};
function pointBizText(t) { return POINT_BIZ[t] || t; }

Page({
  data: {
    balance: 0, totalEarned: 0, signedToday: false, signing: false,
    config: null,
    // 抵现试算（金额单位：分）
    previewUsed: '', previewPayable: '', previewDiscountText: null, previewing: false,
    // 积分明细（分页，IPage → {records,total}）
    logs: [], page: 1, size: 20, total: 0, loading: false, finished: false, loadErr: false
  },

  onShow() {
    this.loadBalance();
    this.loadConfig();
    this.loadLogs(true);
  },

  loadBalance() {
    api.pointBalance().then((p) => {
      this.setData({ balance: p.balance || 0, totalEarned: p.totalEarned || 0 });
    }).catch(() => {});
  },

  loadConfig() {
    api.pointConfig().then((c) => this.setData({ config: c })).catch(() => {});
  },

  loadLogs(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, loadErr: false });
    api.pointLogs(page, this.data.size).then((p) => {
      const recs = (p.records || []).map((l) => Object.assign({}, l, {
        bizText: pointBizText(l.bizType),
        deltaText: (l.delta > 0 ? '+' : '') + l.delta,
        deltaClass: l.delta > 0 ? 'up' : 'down',
        createdAt: l.createdAt || ''
      }));
      const list = reset ? recs : this.data.logs.concat(recs);
      this.setData({ logs: list, page, total: p.total || 0, loading: false, finished: list.length >= (p.total || 0) });
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  onReachBottom() { if (!this.data.finished) this.loadLogs(false); },
  onPullDownRefresh() { this.loadBalance(); this.loadLogs(true); wx.stopPullDownRefresh(); },

  signin() {
    if (this.data.signing) return;
    this.setData({ signing: true });
    api.pointSignin().then((gain) => {
      this.setData({ signing: false, signedToday: true });
      wx.showToast({ title: '签到 +' + gain + ' 积分', icon: 'success' });
      this.loadBalance();
      this.loadLogs(true);
    }).catch((e) => {
      this.setData({ signing: false });
      wx.showToast({ title: (e && e.msg) || '签到失败', icon: 'none' });
    });
  },

  onUsedInput(e) { this.setData({ previewUsed: e.detail.value, previewDiscountText: null }); },
  onPayableInput(e) { this.setData({ previewPayable: e.detail.value, previewDiscountText: null }); },

  preview() {
    const used = parseInt(this.data.previewUsed, 10);
    const payableFen = yuanToFen(this.data.previewPayable);
    if (!used || used <= 0) { wx.showToast({ title: '请输入使用积分数', icon: 'none' }); return; }
    if (payableFen < 0) { wx.showToast({ title: '请输入有效应付金额', icon: 'none' }); return; }
    this.setData({ previewing: true });
    api.pointPreview(used, payableFen).then((discountFen) => {
      this.setData({ previewing: false, previewDiscountText: formatPrice(discountFen) });
    }).catch(() => this.setData({ previewing: false }));
  }
});
