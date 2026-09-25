const api = require('../../utils/api.js');
const { formatPrice, formatTime } = require('../../utils/util.js');

// 钱包流水类型中文（后端 FundFlow.type：SETTLE/FREEZE/WITHDRAW/UNFREEZE）
const FLOW_TYPE = {
  SETTLE: '结算入账',
  FREEZE: '提现冻结',
  WITHDRAW: '提现到账',
  UNFREEZE: '驳回解冻'
};

Page({
  data: {
    withdrawable: 0, frozen: 0, settledTotal: 0,
    withdrawableText: '0.00', frozenText: '0.00', settledText: '0.00',
    flows: [], loading: true, loadErr: false
  },

  onShow() { this.loadAll(); },

  loadAll() {
    this.setData({ loading: true, loadErr: false });
    api.walletBalance().then((b) => {
      this.setData({
        withdrawable: b.withdrawable || 0,
        frozen: b.frozen || 0,
        settledTotal: b.settledTotal || 0,
        withdrawableText: formatPrice(b.withdrawable || 0),
        frozenText: formatPrice(b.frozen || 0),
        settledText: formatPrice(b.settledTotal || 0)
      });
      return api.walletFlows(1, 30);
    }).then((p) => {
      const flows = (p.items || []).map((f) => Object.assign({}, f, {
        amountText: (f.direction === 'IN' ? '+' : '-') + formatPrice(f.amount),
        typeText: FLOW_TYPE[f.type] || f.type,
        timeText: formatTime(f.createdAt)
      }));
      this.setData({ flows, loading: false });
    }).catch(() => {
      this.setData({ loading: false, loadErr: true });
    });
  },

  goWithdraw() {
    wx.navigateTo({ url: '/pages/wallet/withdraw/withdraw?balance=' + this.data.withdrawable });
  },

  onPullDownRefresh() {
    this.loadAll();
    wx.stopPullDownRefresh();
  }
});
