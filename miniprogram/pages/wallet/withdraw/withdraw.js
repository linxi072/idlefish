const api = require('../../utils/api.js');
const { formatPrice, yuanToFen } = require('../../utils/util.js');

Page({
  data: {
    balance: 0, balanceText: '0.00',
    amount: '', amountFen: 0,
    account: '',
    submitting: false, canSubmit: false
  },

  onLoad(query) {
    const balance = Number(query.balance) || 0;
    this.setData({ balance, balanceText: formatPrice(balance) });
  },

  // 金额输入（元）→ 分，超可提现或非法则不可提交
  onAmount(e) {
    const v = e.detail.value;
    const fen = yuanToFen(v);
    this.setData({
      amount: v,
      amountFen: fen,
      canSubmit: fen > 0 && fen <= this.data.balance && this.data.account.trim() && !this.data.submitting
    });
  },

  // 全部提现：金额直接取可提现余额（分）
  allIn() {
    const yuan = (this.data.balance / 100).toFixed(2);
    this.setData({
      amount: yuan,
      amountFen: this.data.balance,
      canSubmit: this.data.balance > 0 && this.data.account.trim() && !this.data.submitting
    });
  },

  onAccount(e) {
    const account = e.detail.value;
    this.setData({
      account,
      canSubmit: this.data.amountFen > 0 && this.data.amountFen <= this.data.balance && account.trim() && !this.data.submitting
    });
  },

  submit() {
    if (this.data.submitting) return;
    const fen = this.data.amountFen;
    if (fen <= 0) { wx.showToast({ title: '请输入提现金额', icon: 'none' }); return; }
    if (fen > this.data.balance) { wx.showToast({ title: '超过可提现余额', icon: 'none' }); return; }
    const account = (this.data.account || '').trim();
    if (!account) { wx.showToast({ title: '请输入收款账号', icon: 'none' }); return; }

    this.setData({ submitting: true, canSubmit: false });
    api.walletWithdraw(fen, account)
      .then(() => {
        wx.showToast({ title: '提现申请已提交', icon: 'success' });
        setTimeout(() => {
          const pages = getCurrentPages();
          const prev = pages[pages.length - 2];
          if (prev && prev.loadAll) prev.loadAll();
          wx.navigateBack();
        }, 800);
      })
      .catch((e) => {
        wx.showToast({ title: (e && e.msg) || '提现失败', icon: 'none' });
        this.setData({ submitting: false, canSubmit: fen > 0 && fen <= this.data.balance && account });
      });
  }
});
