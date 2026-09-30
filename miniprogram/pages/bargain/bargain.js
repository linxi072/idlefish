const api = require('../../utils/api.js');
const { formatPrice, yuanToFen, formatTime } = require('../../utils/util.js');

// 议价状态中文（对齐 Bargain.status：pending/accepted/rejected/expired）
const BARGAIN_STATUS = { pending: '议价中', accepted: '已接受', rejected: '已拒绝', expired: '已失效' };
function bargainStatusText(s) { return BARGAIN_STATUS[s] || s; }

Page({
  data: {
    convId: '', sellerId: '', itemId: '', itemTitle: '', itemImg: '', origPriceText: '0.00',
    offerText: '', submitting: false,
    records: [], loading: false
  },

  onLoad(options) {
    const origPriceFen = Number(options.origPriceFen) || 0;
    this.setData({
      convId: options.convId || '', sellerId: options.sellerId || '', itemId: options.itemId || '',
      itemTitle: decodeURIComponent(options.itemTitle || ''), itemImg: options.itemImg || '',
      origPriceText: formatPrice(origPriceFen)
    });
    this.loadRecords();
  },

  loadRecords() {
    if (!this.data.convId) return;
    this.setData({ loading: true });
    api.listBargains(this.data.convId).then((list) => {
      const recs = (list || []).map((b) => Object.assign({}, b, {
        offerText: formatPrice(b.offerPrice),
        statusText: bargainStatusText(b.status),
        createdAt: b.createdAt || '',
        expireText: b.expireAt ? formatTime(b.expireAt) : ''
      }));
      this.setData({ records: recs, loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  onOfferInput(e) { this.setData({ offerText: e.detail.value }); },

  submit() {
    const fen = yuanToFen(this.data.offerText);
    if (fen < 0) { wx.showToast({ title: '请输入有效出价', icon: 'none' }); return; }
    if (!this.data.convId) { wx.showToast({ title: '缺少会话信息', icon: 'none' }); return; }
    this.setData({ submitting: true });
    // 金额单位：分（前端元→分）；24h 有效（后端 expireAt 控制）
    api.createBargain({
      convId: this.data.convId, sellerId: Number(this.data.sellerId),
      itemId: Number(this.data.itemId), offerPrice: fen
    }).then(() => {
      this.setData({ submitting: false, offerText: '' });
      wx.showToast({ title: '出价已发送', icon: 'success' });
      this.loadRecords();
    }).catch((e) => {
      this.setData({ submitting: false });
      wx.showToast({ title: (e && e.msg) || '出价失败', icon: 'none' });
    });
  }
});
