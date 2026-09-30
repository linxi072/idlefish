const api = require('../../utils/api.js');

Page({
  data: {
    level: null, benefits: []
  },

  onShow() {
    api.memberMine().then((v) => {
      // 权益标签：按 MemberLevelView 的布尔/数值字段展示（千分比 commissionDiscount）
      const benefits = [];
      if (v.freeShipping) benefits.push({ icon: '🚚', name: '全场免运费' });
      if (v.priorityReview) benefits.push({ icon: '⚡', name: '优先审核' });
      if (v.commissionDiscount) benefits.push({ icon: '💎', name: '佣金减免 ' + v.commissionDiscount + '‰' });
      this.setData({ level: v, benefits });
    }).catch(() => {});
  }
});
