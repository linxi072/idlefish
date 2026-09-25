// pages/evaluate/list/list.js —— 我的评价 / 我收到的（Tab 切换）
const api = require('../../../utils/api.js');
const { ratingArray, reviewRoleLabel } = require('../../../utils/util.js');

// 装饰：补齐星标数组、角色中文、评价人展示名（匿名→匿名用户，否则 用户#{id}）
function decorate(list) {
  return (list || []).map(r => Object.assign({}, r, {
    ratingArr: ratingArray(r.rating),
    roleLabel: reviewRoleLabel(r.role),
    peerName: r.anonymous ? '匿名用户' : ('用户' + r.reviewerId)
  }));
}

Page({
  data: {
    tab: 'my',          // my=我发出的 / received=我收到的
    myList: [],
    receivedList: [],
    loading: true
  },

  onShow() { this.load(); },

  load() {
    this.setData({ loading: true });
    Promise.all([api.myReviews(), api.receivedReviews()]).then((res) => {
      this.setData({
        myList: decorate(res[0]),
        receivedList: decorate(res[1]),
        loading: false
      });
    }).catch(() => { this.setData({ loading: false }); });
  },

  switchTab(e) { this.setData({ tab: e.currentTarget.dataset.tab }); },

  goItem(e) {
    const id = e.currentTarget.dataset.id;
    if (id) wx.navigateTo({ url: '/pages/item-detail/item-detail?id=' + id });
  }
});
