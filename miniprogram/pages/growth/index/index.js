const api = require('../../../utils/api.js');

Page({
  data: {
    account: null, config: null,
    levelName: '', growthValue: 0, totalEarned: 0,
    percent: 0, nextLevelName: '', growthToNext: 0,
    frozen: false, daysUntilDecay: 0, decayNotifyDays: 0,
    decayWarning: false, touching: false
  },

  onShow() {
    this.loadAccount();
    this.loadConfig();
    this.touch(); // 进入成长中心即上报活跃，刷新衰减基准
  },

  loadAccount() {
    api.growthAccount().then((a) => {
      this.setData({
        account: a,
        levelName: a.levelName || a.levelCode || '',
        growthValue: a.growthValue || 0,
        totalEarned: a.totalEarned || 0,
        percent: Math.round((a.percent || 0) * 100),
        nextLevelName: a.nextLevelName || '',
        growthToNext: a.growthToNext || 0,
        frozen: !!a.frozen,
        daysUntilDecay: a.daysUntilDecay || 0,
        decayNotifyDays: a.decayNotifyDays || 0,
        decayWarning: (a.daysUntilDecay || 999) <= (a.decayNotifyDays || 0)
      });
    }).catch(() => {});
  },

  loadConfig() {
    api.growthConfig().then((c) => this.setData({ config: c })).catch(() => {});
  },

  touch() {
    if (this.data.touching) return;
    this.setData({ touching: true });
    api.growthTouch().catch(() => {}).then(() => this.setData({ touching: false }));
  },

  goLogs() { wx.navigateTo({ url: '/pages/growth/logs/logs' }); },
  goBenefits() { wx.navigateTo({ url: '/pages/growth/benefits/benefits' }); },
  goExchanges() { wx.navigateTo({ url: '/pages/growth/exchanges/exchanges' }); }
});
