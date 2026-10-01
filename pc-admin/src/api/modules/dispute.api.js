import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const disputeApi = {
  list: (status, keyword, page = 1, size = 20) => USE_MOCK
    ? (() => {
        const all = mock.disputeList().filter(d =>
          (!status || d.status === status) &&
          (!keyword || (d.disputeNo || '').includes(keyword) || (d.orderNo || '').includes(keyword)));
        const start = (page - 1) * size;
        return Promise.resolve({ list: all.slice(start, start + size), total: all.length });
      })()
    : req('GET', '/api/admin/dispute/list', null, { status, keyword, page, size }).then(pageTo),
  detail: (id) => USE_MOCK ? Promise.resolve(mock.disputeList().find(d => d.id === id) || null)
    : req('GET', '/api/admin/dispute/detail', null, { id }),
  // 平台裁决：仅落库裁决结果与裁决退款金额，实际退款由既有退款链路执行
  resolve: (id, result, refundAmount, remark) => USE_MOCK ? (() => {
    const d = mock.disputeList().find(x => x.id === id);
    if (d) {
      d.status = 'RESOLVED';
      d.result = result;
      d.refundAmount = refundAmount || 0;
      d.platformRemark = remark || '';
    }
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/dispute/resolve', { id, result, refundAmount, remark }),
  close: (id) => USE_MOCK ? (() => {
    const d = mock.disputeList().find(x => x.id === id);
    if (d) d.status = 'CLOSED';
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/dispute/close', null, { id })
};
