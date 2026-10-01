import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const couponApi = {
  list: (options) => {
    options = options || {};
    if (USE_MOCK) {
      let list = mock.coupons.slice();
      if (options.status) list = list.filter(c => c.status === options.status);
      if (options.keyword) list = list.filter(c => (c.name || '').includes(options.keyword));
      return Promise.resolve({ list, total: list.length });
    }
    return req('GET', '/api/admin/coupon', null,
      { status: options.status, keyword: options.keyword, page: options.page || 1, size: options.size || 20 }).then(pageTo);
  },
  create: (d) => USE_MOCK ? (() => {
    const id = Math.max(0, ...mock.coupons.map(c => c.id)) + 1;
    const c = Object.assign({ id, claimedCount: 0, status: 'ACTIVE' }, d);
    mock.coupons.push(c);
    return Promise.resolve(c);
  })() : req('POST', '/api/admin/coupon/create', d),
  setStatus: (couponId, status) => USE_MOCK ? (() => {
    const c = mock.coupons.find(x => x.id === couponId);
    if (c) c.status = status;
    return Promise.resolve({ ok: true });
  })() : req('POST', '/api/admin/coupon/status', null, { couponId, status })
};
