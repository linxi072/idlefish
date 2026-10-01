import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const memberApi = {  listTiers: () => USE_MOCK ? Promise.resolve(mock.memberLevels.slice())
    : req('GET', '/api/admin/member/level/list'),
  saveTier: (d) => USE_MOCK ? (() => {
    const list = mock.memberLevels;
    if (d.id) {
      const i = list.findIndex(t => t.id === d.id);
      if (i >= 0) Object.assign(list[i], d);
    } else {
      const id = Math.max(0, ...list.map(t => t.id)) + 1;
      list.push(Object.assign({ id, sortOrder: list.length + 1 }, d));
    }
    return Promise.resolve(d.id ? d.id : id);
  })() : req('POST', '/api/admin/member/level/save', d),
  deleteTier: (id) => USE_MOCK ? (() => {
    const i = mock.memberLevels.findIndex(t => t.id === id);
    if (i >= 0) mock.memberLevels.splice(i, 1);
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/member/level/delete', null, { id }),
  growthList: (page, size) => USE_MOCK
    ? (() => {
        const all = mock.memberGrowthList();
        const start = (page - 1) * size;
        return Promise.resolve({ list: all.slice(start, start + size), total: all.length });
      })()
    : req('GET', '/api/admin/member/level/growth', null, { page, size }).then(pageTo),
  mine: () => USE_MOCK ? Promise.resolve(mock.memberMyLevel())
    : req('GET', '/api/member/level/mine')
};
