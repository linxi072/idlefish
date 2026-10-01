import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const marketingApi = {
  overview: () => USE_MOCK ? Promise.resolve(mock.couponAnalyticsOverview())
    : req('GET', '/api/admin/marketing/coupon/overview'),
  typeDist: () => USE_MOCK ? Promise.resolve(mock.couponAnalyticsTypeDist())
    : req('GET', '/api/admin/marketing/coupon/type-dist')
};
