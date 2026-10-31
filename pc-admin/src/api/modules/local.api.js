import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

// pc-admin/src/api/modules/local.api.js —— ITER-F05 同城回收与本地履约（运营后台）
// 真实接口见 AdminLocalController /api/admin/local/*（F-05 后端已完整实现）。
// 字段金额单位统一「分」，前端 yuan() 折算展示；列表经 pageTo 归一为 { list, total }。

export const localApi = {
  // ===== 回收单 =====
  recycleList: (status, keyword, page = 1, size = 20) => USE_MOCK
    ? (() => {
        let all = mock.recycleList();
        if (status) all = all.filter(d => d.status === status);
        if (keyword) all = all.filter(d =>
          (d.recycleNo || '').includes(keyword) ||
          (d.title || '').includes(keyword) ||
          (d.contactName || '').includes(keyword));
        const start = (page - 1) * size;
        return Promise.resolve({ list: all.slice(start, start + size), total: all.length });
      })()
    : req('GET', '/api/admin/local/recycle/list', null, { status, keyword, page, size }).then(pageTo),

  recycleDetail: (id) => USE_MOCK
    ? Promise.resolve(mock.recycleList().find(d => d.id === id) || null)
    : req('GET', '/api/admin/local/recycle/detail', null, { id }),

  // 揽收（PENDING_PICKUP → PICKED_UP）
  pickup: (id) => USE_MOCK ? (() => {
    const d = mock.recycleList().find(x => x.id === id);
    if (d) d.status = 'PICKED_UP';
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/recycle/pickup', null, { id }),

  // 开始质检（PICKED_UP → INSPECTING）
  inspecting: (id) => USE_MOCK ? (() => {
    const d = mock.recycleList().find(x => x.id === id);
    if (d) d.status = 'INSPECTING';
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/recycle/inspecting', null, { id }),

  // 质检调价（INSPECTING/PICKED_UP → QUOTED，服务端二次封顶）
  quote: (id, finalAmount, quoteRemark) => USE_MOCK ? (() => {
    const d = mock.recycleList().find(x => x.id === id);
    if (d) { d.status = 'QUOTED'; d.finalAmount = finalAmount; d.quoteRemark = quoteRemark || ''; }
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/recycle/quote', { id, finalAmount, quoteRemark }),

  // 放款（CONFIRMED → PAID，仅用户已确认报价）
  pay: (id) => USE_MOCK ? (() => {
    const d = mock.recycleList().find(x => x.id === id);
    if (d) d.status = 'PAID';
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/recycle/pay', null, { id }),

  // 明确不可用（城市未开通/服务商异常）
  unavailable: (id, reason) => USE_MOCK ? (() => {
    const d = mock.recycleList().find(x => x.id === id);
    if (d) { d.status = 'UNAVAILABLE'; d.cancelReason = reason || ''; }
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/recycle/unavailable', null, { id, reason }),

  // ===== 同城配送单 =====
  deliveryList: (status, bizNo, city, page = 1, size = 20) => USE_MOCK
    ? (() => {
        let all = mock.deliveryList();
        if (status) all = all.filter(d => d.status === status);
        if (bizNo) all = all.filter(d => (d.bizNo || '').includes(bizNo));
        if (city) all = all.filter(d => (d.city || '').includes(city));
        const start = (page - 1) * size;
        return Promise.resolve({ list: all.slice(start, start + size), total: all.length });
      })()
    : req('GET', '/api/admin/local/delivery/list', null, { status, bizNo, city, page, size }).then(pageTo),

  deliveryDetail: (deliveryNo) => USE_MOCK
    ? Promise.resolve(mock.deliveryDetail(deliveryNo))
    : req('GET', '/api/admin/local/delivery/detail', null, { deliveryNo }),

  // 状态推进（DELIVERING / ARRIVED / SIGNED，服务端校验状态机）
  advance: (deliveryNo, status) => USE_MOCK ? (() => {
    const d = mock.deliveryList().find(x => x.deliveryNo === deliveryNo);
    if (d) d.status = status;
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/local/delivery/advance', null, { deliveryNo, status })
};
