// pc-admin/src/api/index.js —— 接口层聚合出口（桶文件）
// 视图统一 `import { adminApi, systemApi, ... } from '@/api'`，无需关心底层按域拆分。
export * from './core.js';
export { adminApi } from './modules/admin.api.js';
export { systemApi } from './modules/system.api.js';
export { walletApi } from './modules/wallet.api.js';
export { attributeApi } from './modules/attribute.api.js';
export { notifyApi } from './modules/notify.api.js';
export { couponApi } from './modules/coupon.api.js';
export { analyticsApi } from './modules/analytics.api.js';
export { marketingApi } from './modules/marketing.api.js';
export { memberApi } from './modules/member.api.js';
export { disputeApi } from './modules/dispute.api.js';
export { reviewApi } from './modules/review.api.js';
export { activityApi } from './modules/activity.api.js';
export { inviteApi } from './modules/invite.api.js';
export { recommendApi } from './modules/recommend.api.js';
export { searchTermApi } from './modules/search-term.api.js';
export { toCsv, downloadCsv } from '@/utils/csv';
