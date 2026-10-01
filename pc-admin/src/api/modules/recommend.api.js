// pc-admin/src/api/modules/recommend.api.js —— 首页推荐预览（F-14.2，对齐 SearchController /api/search/recommend，免登录）
// 买家端推荐流：同城优先 + 热度半衰期衰减 + 行为加权 + 冷启动保量（后端 RecommendService.feed）
// 从单体 api.js 按域拆分；视图层仍通过 '@/api' 的 re-export 导入，无需改动。
//
// 契约字段对照（与后端 SearchController 一致，金额单位：分）：
//  - GET /api/search/recommend?city&userId&page&size -> PageData<RecommendItem>  推荐流（分页 IPage）
// RecommendItem: { id, title, priceFen, image, city, score }
import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const recommendApi = {
  recommend: (city, userId, page, size) => USE_MOCK
    ? Promise.resolve(mock.recommend(city, page, size))
    : req('GET', '/api/search/recommend', null, { city, userId, page, size }).then(pageTo)
};
