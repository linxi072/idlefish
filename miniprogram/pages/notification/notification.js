const api = require('../../utils/api.js');
const { formatTime, notifyTypeText, notifyIcon } = require('../../utils/util.js');

Page({
  data: {
    list: [], page: 1, size: 20, total: 0,
    loading: false, finished: false, unread: 0
  },

  onLoad() {
    this.loadUnread();
    this.reload();
  },

  onShow() { this.loadUnread(); },

  loadUnread() {
    api.notifyUnread().then((n) => this.setData({ unread: n || 0 })).catch(() => {});
  },

  reload() {
    this.setData({ page: 1, list: [], finished: false });
    this.fetch();
  },

  fetch() {
    if (this.data.loading || this.data.finished) return;
    this.setData({ loading: true });
    api.notifyList(this.data.page, this.data.size).then((p) => {
      const records = (p && p.records) || [];
      const items = records.map((n) => Object.assign({}, n, {
        typeText: notifyTypeText(n.type),
        icon: notifyIcon(n.type),
        timeT: formatTime(n.createdAt)
      }));
      const list = this.data.list.concat(items);
      const total = (p && p.total) || 0;
      const page = this.data.page + 1;
      this.setData({ list, total, page, loading: false, finished: list.length >= total });
    }).catch(() => this.setData({ loading: false }));
  },

  onReachBottom() { this.fetch(); },

  onPullDownRefresh() {
    this.reload();
    wx.stopPullDownRefresh();
  },

  // 单条已读
  markRead(e) {
    const id = e.currentTarget.dataset.id;
    api.notifyRead(id).then(() => {
      this.setData({ list: this.data.list.map((n) => (n.id === id ? Object.assign({}, n, { read: 1 }) : n)) });
      this.loadUnread();
    }).catch(() => {});
  },

  // 全部已读
  markAll() {
    api.notifyReadAll().then(() => {
      this.setData({ list: this.data.list.map((n) => Object.assign({}, n, { read: 1 })), unread: 0 });
      wx.showToast({ title: '已全部已读', icon: 'success' });
    }).catch(() => {});
  }
});
