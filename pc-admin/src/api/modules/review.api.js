import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const reviewApi = {
  // 待审评价列表（后端仅返 status=0；mock 同样收敛为待审队列，审核后移出）
  list: () => USE_MOCK ? (() => {
    const list = mock.reviews.filter(r => r.status === 0);
    return Promise.resolve({ list: list.slice(), total: list.length });
  })() : req('GET', '/api/admin/reviews').then(a => ({ list: a || [], total: (a || []).length })),
  approve: (id) => USE_MOCK ? (() => {
    const r = mock.reviews.find(x => x.id === id);
    if (r) r.status = 1;
    return Promise.resolve({ ok: true });
  })() : req('POST', `/api/admin/reviews/${id}/approve`),
  reject: (id, reason) => USE_MOCK ? (() => {
    const r = mock.reviews.find(x => x.id === id);
    if (r) { r.status = 2; r.rejectReason = reason || ''; }
    return Promise.resolve({ ok: true });
  })() : req('POST', `/api/admin/reviews/${id}/reject`, null, { reason })
};
