// pc-admin/src/api/modules/growth.api.js —— 成长值运营后台接口（ITER-F07 / PRD §六）
// 对齐 AdminGrowthController /api/admin/growth/*：规则配置、账户与流水对账、刷分冻结解冻、
// 权益目录配置、兑换记录、衰减手动触发、存量初始化、申诉回补。
import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const growthApi = {
  // 成长值规则配置（只读）
  config: () => USE_MOCK
    ? Promise.resolve(mock.growthConfig)
    : req('GET', '/api/admin/growth/config'),

  // 账户列表（分页，frozenOnly 过滤）
  accounts: (page, size, frozenOnly) => USE_MOCK
    ? (() => {
        let list = mock.growthAccounts.slice();
        if (frozenOnly) list = list.filter(a => a.frozen);
        const start = (page - 1) * size;
        return Promise.resolve({ list: list.slice(start, start + size), total: list.length });
      })()
    : req('GET', '/api/admin/growth/accounts', null, { page, size, frozenOnly }).then(pageTo),

  // 单个账户（只读）
  account: (userId) => USE_MOCK
    ? Promise.resolve(mock.growthAccounts.find(a => a.userId === userId) || null)
    : req('GET', '/api/admin/growth/account', null, { userId }),

  // 指定用户流水（bizType 过滤）
  logs: (userId, bizType, page, size) => USE_MOCK
    ? (() => {
        let list = mock.growthLogs.filter(l => l.userId === userId && (!bizType || l.bizType === bizType));
        const start = (page - 1) * size;
        return Promise.resolve({ list: list.slice(start, start + size), total: list.length });
      })()
    : req('GET', '/api/admin/growth/logs', null, { userId, bizType, page, size }).then(pageTo),

  // 刷分冻结名单（frozenOnly=true）
  antifraud: (page, size) => growthApi.accounts(page, size, true),

  // 冻结发放通道（REQ-03）
  freeze: (userId, reason) => USE_MOCK
    ? (() => {
        const a = mock.growthAccounts.find(x => x.userId === userId);
        if (a) a.frozen = true;
        return Promise.resolve(true);
      })()
    : req('POST', '/api/admin/growth/freeze', null, { userId, reason }),

  // 解冻发放通道
  unfreeze: (userId) => USE_MOCK
    ? (() => {
        const a = mock.growthAccounts.find(x => x.userId === userId);
        if (a) a.frozen = false;
        return Promise.resolve(true);
      })()
    : req('POST', '/api/admin/growth/unfreeze', null, { userId }),

  // 权益目录（全部）
  benefits: () => USE_MOCK
    ? Promise.resolve(mock.growthBenefits.slice())
    : req('GET', '/api/admin/growth/benefits'),

  // 新增 / 更新权益（upsert）
  saveBenefit: (d) => USE_MOCK
    ? (() => {
        const list = mock.growthBenefits;
        if (d.id) {
          const i = list.findIndex(b => b.id === d.id);
          if (i >= 0) Object.assign(list[i], d);
        } else {
          const id = Math.max(0, ...list.map(b => b.id)) + 1;
          list.push(Object.assign({ id }, d));
        }
        return Promise.resolve(d.id || id);
      })()
    : req('POST', '/api/admin/growth/benefits/save', d),

  // 删除权益
  deleteBenefit: (id) => USE_MOCK
    ? (() => {
        const i = mock.growthBenefits.findIndex(b => b.id === id);
        if (i >= 0) mock.growthBenefits.splice(i, 1);
        return Promise.resolve({ ok: true });
      })()
    : req('POST', '/api/admin/growth/benefits/delete', null, { id }),

  // 权益上下架
  updateBenefitStatus: (id, status) => USE_MOCK
    ? (() => {
        const b = mock.growthBenefits.find(x => x.id === id);
        if (b) b.status = status;
        return Promise.resolve(true);
      })()
    : req('POST', '/api/admin/growth/benefits/status', null, { id, status }),

  // 兑换记录（分页，userId 过滤）
  exchanges: (page, size, userId) => USE_MOCK
    ? (() => {
        let list = mock.growthExchanges.slice();
        if (userId) list = list.filter(e => e.userId === userId);
        const start = (page - 1) * size;
        return Promise.resolve({ list: list.slice(start, start + size), total: list.length });
      })()
    : req('GET', '/api/admin/growth/exchanges', null, { page, size, userId }).then(pageTo),

  // 手动触发衰减（notify=true 预警 / false 扫描衰减）
  runDecay: (notify) => USE_MOCK
    ? Promise.resolve(mock.growthDecaySummary)
    : req('POST', '/api/admin/growth/decay/run', null, { notify }),

  // 存量用户成长值账户初始化（PRD §12.4 遗留项②）
  initStock: (limit) => USE_MOCK
    ? Promise.resolve(mock.growthAccounts.length)
    : req('POST', '/api/admin/growth/init-stock', null, { limit }),

  // 申诉成功回补判责扣减（REQ-10）
  restoreByDispute: (userId, disputeNo) => USE_MOCK
    ? Promise.resolve(0)
    : req('POST', '/api/admin/growth/restore-by-dispute', null, { userId, disputeNo })
};
