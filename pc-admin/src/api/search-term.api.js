// pc-admin/src/api/search-term.api.js —— 搜索词运营（F-14.3，对齐 SearchTermController /api/search/term/* 与 SearchTermAdminController /api/admin/search-term/*）
// 从单体 api.js 按域拆分；视图层仍通过 api.js 的 re-export 导入，无需改动。
//
// 契约字段对照（与后端契约一致）：
//  [用户侧] GET /api/search/term/hot?limit            -> string[]               热搜词
//  [用户侧] GET /api/search/term/history?userId&limit -> string[]               历史搜索
//  [用户侧] POST /api/search/term/record              -> { ok: boolean }        记录搜索 { userId, word }
//  [管理侧] GET  /api/admin/search-term/synonyms       -> SynonymGroup[]         同义词组
//  [管理侧] POST /api/admin/search-term/synonym        -> { ok: boolean }        新增同义 { word, synonym }
//  [管理侧] POST /api/admin/search-term/block           -> { ok: boolean }        屏蔽词 { word }
//  [管理侧] POST /api/admin/search-term/unblock         -> { ok: boolean }        解屏蔽 { word }
//  [管理侧] POST /api/admin/search-term/hotword/status  -> { ok: boolean }        热词状态 { word, status }
//  [管理侧] GET  /api/admin/search-term/block-words     -> string[]               屏蔽词列表（只读）
//  [管理侧] GET  /api/admin/search-term/hot-words       -> HotWord[]              全部热词含状态（只读）
// SynonymGroup: { word, synonyms: string[] }；HotWord: { word, status, weight }
import { USE_MOCK, req } from '../api-core.js';
import mock from '../mock.js';

export const searchTermApi = {
  // —— 用户侧 ——
  hot: (limit) => USE_MOCK ? Promise.resolve(['iPhone', '华为', '显卡', '自行车', '相机', '游戏机'])
    : req('GET', '/api/search/term/hot', null, { limit: limit || 10 }),
  history: (userId, limit) => USE_MOCK ? Promise.resolve(['iPhone', '相机', '显卡'])
    : req('GET', '/api/search/term/history', null, { userId, limit: limit || 10 }),
  record: (userId, word) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/search/term/record', null, { userId, word }),
  // —— 管理侧（运营后台）——
  synonyms: () => USE_MOCK ? Promise.resolve(mock.searchTermSynonyms())
    : req('GET', '/api/admin/search-term/synonyms', null, {}),
  addSynonym: (word, synonym) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/search-term/synonym', null, { word, synonym }),
  blockWord: (word) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/search-term/block', null, { word }),
  unblockWord: (word) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/search-term/unblock', null, { word }),
  setHotWordStatus: (word, status) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/search-term/hotword/status', null, { word, status }),
  // —— 管理侧只读端点（F-14.3 补全）——
  blockWords: () => USE_MOCK ? Promise.resolve(['赌博', '发票', '代开发票', '博彩', '私彩'])
    : req('GET', '/api/admin/search-term/block-words', null, {}),
  hotWords: () => USE_MOCK ? Promise.resolve(mock.searchTermHotWords())
    : req('GET', '/api/admin/search-term/hot-words', null, {})
};
