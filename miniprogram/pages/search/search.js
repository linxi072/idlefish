// pages/search/search.js —— 搜索页（F-14.3 搜索词运营：热搜榜 + 搜索历史 + 记录 + 检索）
const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

// 当前用户 ID：优先取登录态，mock 缺省回退为 2001（与 mock.js 的 me.id 一致）
function currentUserId() {
  const u = store.getUserInfo();
  return (u && u.id) || 2001;
}

Page({
  data: {
    keyword: '',
    hotWords: [],
    history: [],
    results: [],
    searching: false,
    searched: false
  },

  onShow() {
    this.loadHot();
    this.loadHistory();
  },

  loadHot() {
    api.searchTermHot(10).then((r) => this.setData({ hotWords: r || [] })).catch(() => {});
  },

  loadHistory() {
    api.searchTermHistory(currentUserId(), 10).then((r) => this.setData({ history: r || [] })).catch(() => {});
  },

  onInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  onClear() {
    this.setData({ keyword: '', results: [], searched: false });
  },

  onSearch() {
    const kw = (this.data.keyword || '').trim();
    if (!kw) { wx.showToast({ title: '请输入关键词', icon: 'none' }); return; }
    this.doSearch(kw);
  },

  onHotTap(e) {
    const w = e.currentTarget.dataset.word;
    this.setData({ keyword: w });
    this.doSearch(w);
  },

  onHistoryTap(e) {
    const w = e.currentTarget.dataset.word;
    this.setData({ keyword: w });
    this.doSearch(w);
  },

  // 执行搜索：先记录（best-effort），再检索并展示结果
  doSearch(word) {
    this.setData({ searching: true, searched: true });
    api.searchTermRecord(currentUserId(), word).catch(() => {});
    api.search({ keyword: word, page: 1, size: 20 }).then((r) => {
      this.setData({ results: (r && r.items) || [], searching: false });
    }).catch(() => {
      this.setData({ results: [], searching: false });
      wx.showToast({ title: '搜索失败', icon: 'none' });
    });
  }
});
