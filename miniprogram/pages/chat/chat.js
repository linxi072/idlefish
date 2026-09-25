const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

// IM 消息归一化：补齐 msgId（后端用 seq 兜底）、解析卡片内容、标记 mine
function normalize(m, meId) {
  let card = null;
  if ((m.type === 'product_card' || m.type === 'bargain_card') && typeof m.content === 'string') {
    try { card = JSON.parse(m.content); } catch (e) { card = null; }
  }
  return Object.assign({}, m, {
    msgId: m.msgId || ('s' + m.seq),
    isMe: m.senderId === meId,
    card
  });
}

// 根据 apiBaseUrl 推导 WebSocket 基地址（http→ws / https→wss）
function wsBaseUrl() {
  const app = getApp();
  const base = (app.globalData.apiBaseUrl || '').replace(/\/$/, '');
  return base.replace(/^http/, 'ws');
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
    this.connectWs();
  },

  onShow() {
    // 从后台/其他页返回时若连接已断开则重连
    if (getApp().globalData.useMock) return;
    if (!this.task || !this.wsOpen || this._closed) this.connectWs();
  },

  onHide() { this.closeWs(); },
  onUnload() { this.closeWs(); },

  loadMessages() {
    api.getMessages(this.data.convId).then((list) => {
      const msgs = (list || []).map(m => normalize(m, this.data.meId));
      this.setData({ messages: msgs, bottomId: msgs.length ? 'm' + msgs[msgs.length - 1].seq : '' });
    }).catch(() => {});
  },

  onInput(e) { this.setData({ input: e.detail.value }); },

  // 发送文本：补齐 receiverId / itemId（后端 /api/im/send 用 @RequestParam）
  send() {
    const text = this.data.input.trim();
    if (!text) return;
    const msg = {
      convId: this.data.convId, receiverId: this.data.peerId, itemId: this.data.itemId,
      type: 'text', content: text, senderId: this.data.meId
    };
    this.setData({ input: '' });
    api.sendMessage(msg).then((m) => {
      const msgs = this.data.messages.concat(normalize(m, this.data.meId));
      this.setData({ messages: msgs, bottomId: 'm' + m.seq });
    }).catch(() => wx.showToast({ title: '发送失败', icon: 'none' }));
  },

  // 发送商品卡：同样补齐 receiverId / itemId
  sendProductCard() {
    const content = JSON.stringify({ itemId: this.data.itemId, title: this.data.itemTitle, img: this.data.itemImg, price: this.data.itemPrice });
    api.sendMessage({ convId: this.data.convId, receiverId: this.data.peerId, itemId: this.data.itemId, type: 'product_card', content, senderId: this.data.meId })
      .then((r) => {
        const msgs = this.data.messages.concat(normalize(r, this.data.meId));
        this.setData({ messages: msgs, bottomId: 'm' + r.seq });
      }).catch(() => wx.showToast({ title: '发送失败', icon: 'none' }));
  },

  // 发起议价（金额单位：元 → 分，对齐 BargainCreateDTO.offerPrice）
  bargain() {
    wx.showModal({
      title: '发起议价', editable: true, placeholderText: '输入您的出价（元）',
      success: (r) => {
        if (r.confirm && r.content) {
          const offerYuan = r.content.trim();
          const offerFen = Math.round(parseFloat(offerYuan) * 100);
          if (isNaN(offerFen) || offerFen <= 0) return wx.showToast({ title: '出价无效', icon: 'none' });
          const sellerId = this.data.peerId;
          api.createBargain({ itemId: this.data.itemId, sellerId, convId: this.data.convId, offerPrice: offerFen })
            .then((b) => {
              const content = JSON.stringify({
                itemId: this.data.itemId, title: this.data.itemTitle,
                offer: offerYuan, offerFen, sellerId, bargainId: b.id, status: 'pending'
              });
              return api.sendMessage({ convId: this.data.convId, receiverId: this.data.peerId, itemId: this.data.itemId, type: 'bargain_card', content, senderId: this.data.meId });
            })
            .then((rr) => {
              const msgs = this.data.messages.concat(normalize(rr, this.data.meId));
              this.setData({ messages: msgs, bottomId: 'm' + rr.seq });
            }).catch(() => wx.showToast({ title: '发起失败', icon: 'none' }));
        }
      }
    });
  },

  // 卖家接受议价：同步成交价，并就地更新会话内该议价卡状态为「已接受」
  acceptBargain(e) {
    const bargainId = e.currentTarget.dataset.bid;
    api.acceptBargain(bargainId).then(() => {
      wx.showToast({ title: '已接受，成交价已同步', icon: 'success' });
      // 就地更新本地议价卡（避免仅 reload 后状态不刷新）
      const msgs = this.data.messages.map((m) => {
        if (m.type === 'bargain_card' && m.card && m.card.bargainId === Number(bargainId)) {
          return Object.assign({}, m, { card: Object.assign({}, m.card, { status: 'accepted' }) });
        }
        return m;
      });
      this.setData({ messages: msgs });
    }).catch(() => wx.showToast({ title: '操作失败', icon: 'none' }));
  },

  // ===== IM 实时 WebSocket 接线 =====
  connectWs() {
    if (getApp().globalData.useMock) return; // mock 无 WS 服务，跳过（消息走本地 mock）
    if (this.task && this.wsOpen && !this._closed) return;
    const url = wsBaseUrl() + '/ws/im?token=' + encodeURIComponent(store.getToken());
    let task;
    try { task = wx.connectSocket({ url }); } catch (e) { this.scheduleReconnect(); return; }
    this.task = task;
    this._closed = false;

    task.onOpen(() => {
      this.wsOpen = true;
      this.reconnectDelay = 3000;
      this.startHeartbeat();
    });
    task.onMessage((res) => {
      try {
        const m = JSON.parse(res.data);
        this.appendRemote(m);
      } catch (e) { /* 忽略非 JSON 帧 */ }
    });
    task.onClose(() => {
      this.wsOpen = false;
      this.stopHeartbeat();
      if (!this._closed) this.scheduleReconnect();
    });
    task.onError(() => {
      this.wsOpen = false;
      this.stopHeartbeat();
      if (!this._closed) this.scheduleReconnect();
    });
  },

  // 收到实时消息：按 seq 去重追加（自己发出的不会经 WS 回推，仅接收方收到）
  appendRemote(m) {
    if (m && m.seq == null) return;
    const exists = this.data.messages.some(x => (x.msgId === (m.msgId || ('s' + m.seq))) || x.seq === m.seq);
    if (exists) return;
    const msgs = this.data.messages.concat(normalize(m, this.data.meId));
    this.setData({ messages: msgs, bottomId: 'm' + m.seq });
  },

  startHeartbeat() {
    this.stopHeartbeat();
    // 每 25s 发心跳保活（后端对 "ping" 回 "pong"）
    this.heartbeatTimer = setInterval(() => {
      if (this.task && this.wsOpen) {
        try { this.task.send({ data: 'ping' }); } catch (e) { /* ignore */ }
      }
    }, 25000);
  },

  stopHeartbeat() {
    if (this.heartbeatTimer) { clearInterval(this.heartbeatTimer); this.heartbeatTimer = null; }
  },

  scheduleReconnect() {
    if (this._closed) return;
    if (this.reconnectTimer) return;
    const delay = this.reconnectDelay || 3000;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      // 指数退避（上限 30s）
      this.reconnectDelay = Math.min((this.reconnectDelay || 3000) * 2, 30000);
      this.connectWs();
    }, delay);
  },

  closeWs() {
    this._closed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    if (this.task) {
      try { this.task.close({}); } catch (e) { /* ignore */ }
      this.task = null;
    }
    this.wsOpen = false;
  }
});
