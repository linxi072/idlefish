const api = require('../../../utils/api.js');

// 兑换状态中文（对齐后端 BenefitExchange.status）
const EXC_STATUS = {
  SUCCESS: '兑换成功', PENDING: '处理中', FAILED: '兑换失败'
};
function excStatusText(s) { return EXC_STATUS[s] || s || '未知'; }

Page({
  data: {
    exchanges: [], page: 1, size: 20, total: 0, loading: false, finished: false, loadErr: false
  },

  onShow() { this.loadExchanges(true); },

  loadExchanges(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, loadErr: false });
    api.growthExchanges(page, this.data.size).then((p) => {
      const recs = (p.records || []).map((e) => Object.assign({}, e, {
        statusText: excStatusText(e.status)
      }));
      const list = reset ? recs : this.data.exchanges.concat(recs);
      this.setData({ exchanges: list, page, total: p.total || 0, loading: false, finished: list.length >= (p.total || 0) });
    }).catch(() => this.setData({ loading: false, loadErr: true }));
  },

  onReachBottom() { if (!this.data.finished) this.loadExchanges(false); },
  onPullDownRefresh() { this.loadExchanges(true); wx.stopPullDownRefresh(); }
});
