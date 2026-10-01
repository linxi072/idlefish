import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const analyticsApi = {
  overview: (days) => USE_MOCK ? Promise.resolve(mock.analyticsOverview(days))
    : req('GET', '/api/admin/analytics/overview', null, { days }),
  funnel: (days) => USE_MOCK ? Promise.resolve(mock.analyticsFunnel(days))
    : req('GET', '/api/admin/analytics/funnel', null, { days }),
  category: (days) => USE_MOCK ? Promise.resolve(mock.analyticsCategory(days))
    : req('GET', '/api/admin/analytics/category', null, { days }),
  condition: () => USE_MOCK ? Promise.resolve(mock.analyticsCondition())
    : req('GET', '/api/admin/analytics/condition')
};
