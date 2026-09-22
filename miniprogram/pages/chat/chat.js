const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

function normalize(m, meId) {
  let card = null;
  if ((m.type === 'product_card' || m.type === 'bargain_card') && typeof m.content === 'string') {
    try { card = JSON.parse(m.content); } catch (e) { card = null; }
  }
  return Object.assign({}, m, { isMe: m.senderId === meId, card });
}

Page({
  data: {
    convId: '', peerId: '', itemId: '', itemTitle: '', itemImg: '', itemPrice: '',
    peer: null, messages: [], input: '', meId: 2001, bottomId: ''
  },

  onLoad(options) {
    const me = store.getUserInfo();
    this.setData({
      convId: options.convId, peerId: options.peerId,
      itemId: options.itemId || '', itemTitle: decodeURIComponent(options.itemTitle || ''),
      itemImg: options.itemImg || '', itemPrice: options.itemPrice || '',
      meId: me ? me.id : 2001
    });
    this.loadMessages();
  },

  loadMessages() {
    api.getMessages(this.data.convId).then((list) => {
      const msgs = (list || []).map(m => normalize(m, this.data.meId));
      this.setData({ messages: msgs, bottomId: msgs.length ? 'm' + msgs[msgs.length - 1].seq : '' });
    }).catch(() => {});
  },

  onInput(e) { this.setData({ input: e.detail.value }); },

  send() {
    const text = this.data.input.trim();
    if (!text) return;
    const msg = { convId: this.data.convId, type: 'text', content: text, senderId: this.data.meId };
    this.setData({ input: '' });
    api.sendMessage(msg).then((m) => {
      const msgs = this.data.messages.concat(normalize(m, this.data.meId));
      this.setData({ messages: msgs, bottomId: 'm' + m.seq });
    }).catch(() => wx.showToast({ title: '发送失败', icon: 'none' }));
  },

  sendProductCard() {
    const content = JSON.stringify({ itemId: this.data.itemId, title: this.data.itemTitle, img: this.data.itemImg, price: this.data.itemPrice });
    api.sendMessage({ convId: this.data.convId, type: 'product_card', content, senderId: this.data.meId })
      .then((r) => {
        const msgs = this.data.messages.concat(normalize(r, this.data.meId));
        this.setData({ messages: msgs, bottomId: 'm' + r.seq });
      }).catch(() => {});
  },

  bargain() {
    wx.showModal({
      title: '发起议价', editable: true, placeholderText: '输入您的出价（元）',
      success: (r) => {
        if (r.confirm && r.content) {
          const content = JSON.stringify({ itemId: this.data.itemId, offer: r.content, title: this.data.itemTitle });
          api.sendMessage({ convId: this.data.convId, type: 'bargain_card', content, senderId: this.data.meId })
            .then((rr) => {
              const msgs = this.data.messages.concat(normalize(rr, this.data.meId));
              this.setData({ messages: msgs, bottomId: 'm' + rr.seq });
            }).catch(() => {});
        }
      }
    });
  }
});
