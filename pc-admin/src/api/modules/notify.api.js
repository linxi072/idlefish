import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const notifyApi = {
  list: (page, size) => USE_MOCK
    ? Promise.resolve({ list: mock.notifications.slice(), total: mock.notifications.length })
    : req('GET', '/api/notify/list', null, { page: page || 1, size: size || 20 }),
  unreadCount: () => USE_MOCK
    ? Promise.resolve(mock.notifications.filter(n => !n.read).length)
    : req('GET', '/api/notify/unread-count'),
  markRead: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/notify/read', { id }),
  markAllRead: () => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/notify/read-all')
};
