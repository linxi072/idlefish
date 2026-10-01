import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const activityApi = {
  list: () => USE_MOCK
    ? Promise.resolve({ list: mock.activities.slice(), total: mock.activities.length })
    : req('GET', '/api/activity/list').then(a => ({ list: a || [], total: (a || []).length })),
  create: (d) => USE_MOCK ? (() => {
    const id = Math.max(0, ...mock.activities.map(a => a.id)) + 1;
    const a = Object.assign({ id, soldCount: 0, status: 'ONGOING' }, d);
    mock.activities.push(a);
    return Promise.resolve(a);
  })() : req('POST', '/api/activity/create', d)
};
