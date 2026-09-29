// pc-admin/src/api/invite.api.js —— 邀请拉新接口（F-13.4，对齐 InviteController /api/invite/*）
// 从单体 api.js 按域拆分；视图层仍通过 api.js 的 re-export 导入，无需改动。
//
// 契约字段对照（与后端 InviteController 一致，金额单位：分）：
//  - GET  /api/invite/code?userId            -> { code: string }              当前用户邀请码
//  - POST /api/invite/bind                   -> { ok: boolean }              绑定邀请关系 { userId, code }
//  - GET  /api/invite/invitees?userId        -> { list: Array<InviteRelation>, total }  下线列表（非 IPage）
// InviteRelation: { inviterId, inviteeId, createdAt }
import { USE_MOCK, req } from '../api-core.js';
import mock from '../mock.js';

export const inviteApi = {
  myCode: (userId) => USE_MOCK
    ? Promise.resolve({ code: 'INV' + (userId || 0).toString(36).toUpperCase() + 'XK2P' })
    : req('GET', '/api/invite/code', null, { userId }).then(c => ({ code: typeof c === 'string' ? c : (c && c.code) || '' })),
  bind: (userId, code) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/invite/bind', null, { userId, code }),
  invitees: (userId) => USE_MOCK
    ? (() => {
        const list = mock.inviteRelations.filter(r => r.inviterId === (userId || 2001));
        return Promise.resolve({ list, total: list.length });
      })()
    : req('GET', '/api/invite/invitees', null, { userId }).then(r => ({ list: r || [], total: (r || []).length }))
};
