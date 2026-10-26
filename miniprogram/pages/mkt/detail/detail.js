const api = require('../../utils/api.js');
const { formatPrice } = require('../../utils/util.js');

const KIND_PAGE = { BARGAIN: 'bargain', LOTTERY: 'lottery', SECKILL: 'seckill' };
const KIND_NAME = { BARGAIN: '砍价', LOTTERY: '抽奖', SECKILL: '秒杀' };

// 活动详情（ITER-F08）：展示活动信息与规则，并跳转至对应玩法页
Page({
  data: {
    activityId: '',
    activity: null
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '' });
    this.load();
  },

  load() {
    if (!this.data.activityId) return;
    api.mktActivityDetail(this.data.activityId).then((a) => {
      this.setData({
        activity: a ? Object.assign({}, a, {
          kindName: KIND_NAME[a.kind] || a.kind,
          priceText: formatPrice(a.priceFen),
          originalText: formatPrice(a.originalPriceFen)
        }) : null
      });
    }).catch(() => {});
  },

  goPlay() {
    const a = this.data.activity;
    if (!a) return;
    const page = KIND_PAGE[a.kind] || 'bargain';
    wx.redirectTo({ url: `/pages/mkt/${page}/${page}?activityId=${a.id}` });
  }
});
