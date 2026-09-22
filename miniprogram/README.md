# 闲置集 · 微信小程序（消费者端）

原生小程序工程，对齐《页面原型图 V1.0》与 PRD 字段级规格。

## 目录结构
```
miniprogram/
├── app.js / app.json / app.wxss      全局配置、设计 token、主题
├── project.config.json               开发者工具工程配置
├── sitemap.json
├── utils/
│   ├── request.js                    封装 wx.request（baseUrl + JWT + 401 跳登录）
│   ├── api.js                        统一接口层（useMock 路由 mock / 真实后端）
│   ├── mock.js                       本地 Mock 数据（无需后端即可体验）
│   ├── store.js                      登录态本地存储
│   └── util.js                       格式化 / 状态中文映射
└── pages/
    ├── login/                        微信一键登录 + 手机号
    ├── index/                        首页（搜索 + 推荐瀑布流 + 分类入口 + 发布 FAB）
    ├── category/                     分类频道（类目树 + 筛选 + 排序）
    ├── item-detail/                  商品详情（图集 / 议价 / 联系卖家 / 立即购买）
    ├── publish/                      发布闲置（图片 / 价格 / 类目 / 成色 / 描述）
    ├── order/list/                   订单列表（我买的 / 我卖的）
    ├── order/detail/                 订单详情（支付 / 发货 / 确认收货 / 退款）
    ├── message/                      消息（会话列表）
    ├── chat/                         IM 聊天（文本 / 商品卡 / 议价卡）
    ├── mine/                         我的（资料 / 菜单 / 退出）
    └── address/                      收货地址管理
```

## 运行
1. 微信开发者工具 → 导入项目 → 选择本目录（AppID 可用测试号）。
2. 默认 `app.js` 中 `useMock: true`，直接预览全流程。
3. 联调后端：将 `useMock` 改为 `false`，`apiBaseUrl` 改为后端地址（真机用局域网 IP）。

## 已覆盖的核心闭环
登录 → 浏览/搜索 → 商品详情 → IM 咨询/议价 → 下单（锁库存+快照） → 支付（资金托管） →
卖家发货 → 买家确认收货（结算） → 售后退款。同时接入 12 个埋点事件（`utils/api.js` 的 `track`）。
