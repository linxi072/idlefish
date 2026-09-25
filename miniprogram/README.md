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
    ├── order/detail/                 订单详情（支付 / 发货 / 确认收货 / 售后入口）
    ├── refund/apply/                 申请退款（方式 / 金额 / 原因，F-09）
    ├── refund/detail/                退款详情（状态时间线 + 买卖双方操作，F-09）
    ├── evaluate/apply/               提交评价（星级评分 / 内容 / 匿名，F-09）
    ├── evaluate/list/                我的评价（我发出的 / 我收到的，F-09）
    ├── wallet/index/                 我的钱包（余额概览 + 流水，F-09）
    ├── wallet/withdraw/              提现申请（金额 / 收款账号，F-09）
    ├── notification/                 站内通知中心（类型 / 未读红点 / 单条+全部已读，F-09）
    ├── message/                      消息（IM 会话列表 + Tab 未读角标）
    ├── chat/                         IM 聊天（文本 / 图片 / 商品卡 / 议价卡 + WebSocket 实时）
    ├── mine/                         我的（资料 / 菜单 / 退出 + 系统通知角标）
    └── address/                      收货地址管理
```

## 运行
1. 微信开发者工具 → 导入项目 → 选择本目录（AppID 可用测试号）。
2. 默认 `app.js` 中 `useMock: true`，直接预览全流程。
3. 联调后端：将 `useMock` 改为 `false`，`apiBaseUrl` 改为后端地址（真机用局域网 IP）。

## 已覆盖的核心闭环
登录 → 浏览/搜索 → 商品详情 → IM 咨询/议价 → 下单（锁库存+快照） → 支付（资金托管） →
卖家发货 → 买家确认收货（结算） → 售后退款。同时接入 12 个埋点事件（`utils/api.js` 的 `track`）。

## 售后退款模块（F-09）
| 页面 | 路径 | 说明 |
|---|---|---|
| 申请退款 | `pages/refund/apply/apply` | 退款方式（仅退款/退货退款）、退款金额（默认全额，上限=订单实付）、退款原因（≤200 字） |
| 退款详情 | `pages/refund/detail/detail` | 三节点状态时间线、退款信息、**按角色+状态**动态渲染操作区 |

**入口**：`pages/order/detail` 底部操作区 —— 已有售后单显示「查看售后/处理退款」，否则订单状态允许时显示「申请退款」。

**状态机（严格对齐后端 `RefundService` 守卫）**：

| 角色 | 操作 | 后端守卫条件 |
|---|---|---|
| 买家 | 撤销退款 | 非终态（`refunded/rejected/canceled` 除外） |
| 买家 | 填退货物流 | `退货退款` 且 `wait_seller` 且未填物流 |
| 买家 | 申请平台介入 | 非终态 |
| 卖家 | 同意退款 | `wait_seller` 且 `仅退款` |
| 卖家 | 确认收货并退款 | `wait_seller` 且 `退货退款` |
| 卖家 | 拒绝退款 | `wait_seller`（必填原因，买家可见） |

**可申请退款的订单状态**：后端 `RefundService.apply` 仅允许 `paid`（已支付）/ `shipping`（运输中）订单，
`pending_ship`（待发货）与 `completed`（已完成）**不支持**，前端通过 `util.canApplyRefund` 与之保持一致，避免点了必失败。

**退款进行中（非终态）**页面每 5 秒轮询同步状态，适配卖家 48 小时自动同意与平台裁定。

**金额单位契约**：全链路以「分」为单位，用户输入「元」由 `util.yuanToFen` 转换，展示统一经 `formatPrice` 除 100。

## 评价模块（F-09）
| 页面 | 路径 | 说明 |
|---|---|---|
| 提交评价 | `pages/evaluate/apply/apply` | 星级评分（1-5）、评价内容（≤512 字）、匿名开关；已评价则只读展示；提交后返回订单详情 |
| 我的评价 | `pages/evaluate/list/list` | Tab 切换「我发出的 / 我收到的」，星标 + 内容 + 匿名标签 + 时间，可跳转商品详情 |

**入口**：
- 订单详情（`pages/order/detail`）：仅 `completed`/`closed` 订单显示「评价 / 查看评价」，调 `myReviews` 判定是否已评（严格对齐后端 `ReviewService.submit` 守卫：仅 COMPLETED/CLOSED 可评）。
- 商品详情（`pages/item-detail`）：展示该商品评分汇总（均分 + 条数）与评价列表（`/api/reviews/item/:id`）。
- 「我的」页菜单：「我的评价」→ `pages/evaluate/list/list`。

**状态/契约（对齐后端 `ReviewController` / `ReviewSubmitDTO` / `ReviewService`）**：
- 提交入参：`orderNo` + `rating`(1-5, 必填) + `content`(≤512, 选填) + `anonymous`(0/1)。
- 互评角色：买家→`BUYER_SELLER`，卖家→`SELLER_BUYER`（前端经 `util.orderRoleToReviewRole` 推导）。
- 幂等：同订单、同角色仅一条；已评价时 `myReviews` 命中即只读展示，不再重复提交。
- 内容机审：后端机审通过直接通过并触发被评价方信用重算；mock 默认通过。

## 钱包 / 提现模块（F-09，对齐后端 F-07/F-08 `WalletController` / `WithdrawalService`）
| 页面 | 路径 | 说明 |
|---|---|---|
| 我的钱包 | `pages/wallet/index/index` | 余额概览（可提现 / 冻结中 / 累计结算）+ 钱包流水（IN 绿色 + / OUT 黑色，类型中文：结算入账 / 提现冻结 / 提现到账 / 驳回解冻）；下拉刷新 |
| 提现申请 | `pages/wallet/withdraw/withdraw` | 展示可提现余额、金额输入（元→分校验，超上限拦截）、「全部」一键提现、收款账号输入（脱敏）、提交防重；成功后回写钱包页并返回 |

**入口**：「我的」页菜单「我的钱包」→ `pages/wallet/index/index`。

**余额口径（与后端严格一致）**：`可提现 = 累计结算入账(SETTLE,IN) − 已提现(pending+done)`；冻结中 = pending 提现；退款(REFUND)不计入钱包，避免买家原路退款污染卖家可提现余额。

**提现流程（对齐 `WithdrawalService`）**：提交即冻结（FREEZE 出账流水，余额随 `pending` 计入冻结）→ 后端审批通过实际出账（WITHDRAW）/ 驳回解冻（UNFREEZE）。前端在 `walletWithdraw` 先做金额守卫（`amount>0` 且 `≤可提现`），账号经 `maskAccount` 脱敏（前 2 后 2）。

**金额单位契约**：余额/流水/提现金额全链路「分」；用户输入「元」由 `util.yuanToFen` 转换后传入（后端 `amount` 参数即分），展示统一经 `formatPrice` 除 100。

## 站内通知模块（F-09，对齐后端 `/api/notify` `NotificationController` / `NotificationVO`）
| 页面 | 路径 | 说明 |
|---|---|---|
| 通知中心 | `pages/notification/notification` | 通知列表（类型图标 + 标题 + 内容 + 时间 + 未读红点）、下拉刷新、上拉分页、单条已读、全部已读、空态 |

**入口**：「我的」页菜单「系统通知」→ `pages/notification/notification`；菜单右侧显示未读红点（由 `api.notifyUnread` 驱动）。

**契约（对齐后端 `NotificationService` / `NotificationType`）**：
- 列表：`GET /api/notify/list?page&size` → 后端 `IPage` 归一化为 `{ records, total }`。
- 未读数：`GET /api/notify/unread-count`；单条已读 `POST /api/notify/read {id}`；全部已读 `POST /api/notify/read-all`。
- 通知类型中文与图标经 `util.notifyTypeText` / `util.notifyIcon` 映射（覆盖 16 种 `NotificationType`：订单/退款/结算/提现/审核/发货提醒）。
- 未读红点（`read: 0/1`）驱动列表高亮与「系统通知」菜单角标；进入即按 `id` 标记已读。

## IM 实时通信 + 议价模块（F-09，对齐后端 `/api/im` + `/api/bargain` + `/ws/im`）
**会话 / 消息（对齐 `ImController` / `ConversationVO` / `MessageVO`）**：
- 会话列表 `GET /api/im/conversations`、消息列表 `GET /api/im/messages?convId=`、发送 `POST /api/im/send`（后端用 `@RequestParam`：`receiverId`/`itemId`/`content`/`type`，前端补齐后由 `api.sendMessage` 统一拼 query）、未读 `GET /api/im/unread`。
- **真实模式 VO 映射**：后端 `ConversationVO` 为扁平结构（`peerId`/`peerName`/`itemId`/`lastMessage`/`lastTime`/`unread`），在 `api.getConversations` 中映射成 `message.js` 期望的嵌套 `peer`/`item` 形态；`MessageVO` 无 `msgId`，以 `seq` 兜底 `wx:key`。

**IM 实时 WebSocket（本次新增接线）**：
- `pages/chat/chat.js` 在 `onLoad` 连接 `ws(s)://<apiBaseUrl>/ws/im?token=<JWT>`（鉴权与后端 `AuthHandshakeInterceptor` 一致）。
- 心跳保活：每 25s 发送 `ping`（后端回 `pong`）；`onMessage` 解析 `MessageVO` 并按 `seq` 去重追加（仅接收方收到推送，自己发出的通过 `send` 返回值本地追加，不重复）。
- 断线重连：指数退避（3s 起步，上限 30s）；`onHide`/`onUnload` 关闭连接并清理心跳与重连定时器。
- `mock` 模式无 WS 服务，自动跳过连接（消息走本地 mock）。

**议价（对齐 `BargainController` / `BargainCreateDTO` / `BargainService`）**：
- 发起：`POST /api/bargain/create`（`itemId`/`sellerId`/`convId`/`offerPrice`，**金额「分」**；前端在 `chat` 弹窗输入「元」经 `Math.round(元*100)` 转换）。
- 接受：`POST /api/bargain/accept?bargainId=`（仅卖家，状态守卫 `pending` 且未超 24h，接受后同步成交价到商品）。
- 会话内议价卡：`chat` 内「议价」按钮 → 发出 `bargain_card`，卖家侧显示「接受」；接受成功后就地更新卡片状态为「已接受」。
- ⚠️ 此前 `api.createBargain`/`api.acceptBargain` 缺失导致 `chat` 议价必崩，本次补全并补齐 `mock.createBargain`/`acceptBargain`/`listBargains`。

## Mock 演示数据
`utils/mock.js` 预置两条退款单，`me.id = 2001`：
- `RF20260920001`（订单 NO20260920002，**我是买家**，仅退款，待卖家处理）→ 演示「撤销/平台介入」
- `RF20260919001`（订单 NO20260919003，**我是卖家**，退货退款，待处理）→ 演示「同意/拒绝/确认收货」

`utils/mock.js` 另预置 3 条评价，`me.id = 2001`：
- 我发出的 2 条：买家评卖家（商品 9001，5 分）、卖家评买家（商品 9003，4 分）
- 我收到的 1 条：用户 1001 评我（商品 9002，5 分）
商品详情页 9001/9002/9003 可分别看到对应评价；「我的评价」页可直接查看双向评价。

`utils/mock.js` 另预置钱包演示数据，`me.id = 2001`：
- 2 条 `SETTLE` 结算入账流水（5200 + 4200 分 = ¥94.00 累计结算）→ 初始可提现 ¥94.00、冻结中 ¥0.00。
- 钱包页「提现」→ 输入金额（元）与收款账号 → 提交后新增 `pending` 提现单与一条 `FREEZE` 冻结流水，可提现相应减少；余额口径随提现实时变化（与后端一致）。

`utils/mock.js` 另预置站内通知（5 条，`me.id = 2001`）：订单支付 / 退款申请（未读）/ 结算到账 / 商品过审 / 提现申请（后 3 条已读）→ 演示未读红点与全部已读。

`utils/mock.js` 另预置 IM 会话（`C1`/`C2`）与消息，以及空 `bargains` 集合；在 `chat` 内发起议价将实时写入 `bargains` 并发出 `bargain_card`，卖家接受后状态变为 `accepted`。

> 注：mock 数据中的金额沿用历史写法（如 MacBook `4200`），与其它页面一样被 `formatPrice` 视为「分」展示为 ¥42.00；
> 这是 mock 数据本身的单位不一致（真实后端为「分」，如 420000），不影响真实后端联调。
