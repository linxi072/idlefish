import { USE_MOCK, req } from '@/api/core';
import mock from '@/mock';

// 营销玩法（ITER-F08）：运营端活动配置 / 发布 / 对账 / 监控 / 抽奖奖品
// 金额单位：全链路「分」。字段对齐后端 MarketingActivityCreateDTO / MarketingActivity(entity) / Monitor(Map)。
export const mktActivityApi = {
  // 活动列表（全部状态）：List<MarketingActivity>
  list: () => USE_MOCK
    ? Promise.resolve(mock.mktActivityList())
    : req('GET', '/api/admin/activity/list').then(a => a || []),

  // 活动详情：MarketingActivityVO
  detail: (id) => USE_MOCK
    ? Promise.resolve(mock.mktActivityDetail(id))
    : req('GET', `/api/admin/activity/${id}`),

  // 向导式创建（含亏损阻断 REQ-14，草稿态）：返回活动 id
  create: (dto) => USE_MOCK
    ? Promise.resolve(mock.mktActivityCreate(dto))
    : req('POST', '/api/admin/activity/create', dto),

  // 发布（DRAFT→ONGOING + 预热）：MarketingActivityVO
  publish: (id) => USE_MOCK
    ? Promise.resolve(mock.mktActivityPublish(id))
    : req('POST', `/api/admin/activity/${id}/publish`),

  // 结束并结算对账（REQ-11/REQ-15）：ActivityReconcileVO
  reconcile: (id) => USE_MOCK
    ? Promise.resolve(mock.mktActivityReconcile(id))
    : req('POST', `/api/admin/activity/${id}/reconcile`),

  // 监控报表（REQ-15）：Map{ongoing,draft,ended,joinedToday,totalStock,riskBlocked,subsidyBudgetExceeded,bargainQualified,lotteryWon}
  monitor: () => USE_MOCK
    ? Promise.resolve(mock.mktMonitor())
    : req('GET', '/api/admin/activity/monitor'),

  // 抽奖奖品配置（REQ-09 概率留痕 + REQ-14 亏损阻断）
  savePrizes: (activityId, prizes) => USE_MOCK
    ? Promise.resolve(mock.mktSavePrizes(activityId, prizes))
    : req('POST', `/api/admin/activity/lottery/prizes?activityId=${activityId}`, prizes)
};
