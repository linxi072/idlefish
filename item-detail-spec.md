# 小程序商品详情页（item-detail）需求与实现方案

## 1. 页面定位
商品详情页是「闲置集」C2C 二手交易小程序消费者端的核心转化页，承担"浏览商品 → 建立信任 → 发起购买/沟通"的关键职责。当前已有基础实现（图集、价格、卖家卡片、收藏、立即购买、联系卖家、担保提示、分享），本方案在其基础上补齐字段映射、交互细节、异常流与接口契约，使其具备可联调、可评审、可验收的完成度。

## 2. 现状盘点（已落地代码）
源文件：`miniprogram/pages/item-detail/{item-detail.js, .wxml, .wxss, .json}`
- 数据获取：`api.getItemDetail(id)` → `GET /api/item/detail/{id}`；成功后本地格式化价格、状态文案，并触发埋点 `item_view`。
- 渲染：swiper 图集 + 索引、价格/原价/可议价标签、标题、成色/位置/浏览量标签、卖家卡片（头像/昵称/信用分/实名）、描述、担保提示、底部操作栏（收藏/联系卖家/立即购买）。
- 交互：`toggleFav`（本地状态 + Toast）、`contactSeller`（跳 chat）、`buyNow`（校验在售 → 查地址 → 创建订单 → 跳订单详情）、`onShareAppMessage`。
- 调用链：`request.js`（JWT 封装、401 跳登录）、`api.js`（Mock/真实切换）、`store.js`（token 存储）、`mock.js`（本地 mock 数据）。

## 3. 细化需求
### 3.1 功能需求清单
| 编号 | 功能 | 说明 | 优先级 |
|------|------|------|--------|
| F1 | 商品信息展示 | 图集/价格/原价/成色/位置/浏览量/描述/卖家信息 | P0 |
| F2 | 收藏/取消收藏 | 真实调用收藏接口，状态持久化 | P1 |
| F3 | 联系卖家 | 进入 IM 会话页并带入商品上下文 | P0 |
| F4 | 立即购买 | 校验在售/地址/库存；下单并跳支付 | P0 |
| F5 | 分享 | 微信转发卡片、复制链接 | P1 |
| F6 | 视频播放 | 支持商品视频（后端已返回 videoUrl） | P2 |
| F7 | 相似推荐 | 同类目/同价位商品列表 | P2 |
| F8 | 卖家主页入口 | 查看卖家其他在售商品 | P2 |
| F9 | 想要/议价 | 标记"想要"并触发议价沟通 | P2 |

### 3.2 字段映射表（前端 ↔ 后端 ItemDetailVO）
金额单位：后端以「分」存储与返回（Long），前端需除以 100 展示并格式化为 ¥。

| 展示字段 | 来源字段 | 类型 | 处理 |
|----------|----------|------|------|
| 图集 | images (List<String>) | array | swiper 遍历，images[0] 为封面 |
| 视频 | videoUrl | String/null | 非空则渲染 video |
| 售价 | price | Long(分) | formatPrice(price/100) |
| 原价 | originalPrice | Long(分)/null | 非空展示划线价 |
| 标题 | title | String | 直接展示 |
| 成色 | conditionLevel | Integer 1-5 | 映射：1全新 / 2几乎全新 / 3 95新 / 4 9成新 / 5 功能完好 |
| 位置 | city | String | 可补充 province |
| 浏览量 | viewCount | Integer | "浏览 N" |
| 库存 | stock | Integer | 决定"立即购买"可用性 |
| 状态 | status | String | onsale 可购，其余禁用购买并提示 |
| 描述 | description | String | 文本展示 |
| 卖家昵称 | sellerName | String | 来自 ItemVO 聚合 |
| 卖家头像 | sellerAvatar | String/null | 缺省头像兜底 |
| 卖家信用分 | 暂无 | — | 后端 ItemVO 未返回，需扩展 seller 子对象或调用用户接口 |
| 收藏数 | favCount | Integer | 展示 |
| 点赞数 | likeCount | Integer | 可选展示 |

**重点不一致**：当前 `item-detail.wxml` 直接读取 `item.seller.creditScore` / `item.seller.id` / `item.seller.avatar`，但后端 `ItemDetailVO` 仅聚合 `sellerName` / `sellerAvatar`，未返回 `seller.id` / `creditScore` / 实名认证等。需后端补充 seller 子对象，或前端调整取数逻辑（见 4.2）。

### 3.3 交互流程
- 页面加载：`onLoad(id)` → loading → `getItemDetail` → 格式化（价格/状态/埋点）→ 渲染；失败（不存在/下架）展示 empty 态。
- 立即购买：点击 → 校验 `status==='onsale'` → 取默认/首个地址 → 无地址引导去新增 → `createOrder`（幂等键 `k_{id}_{ts}`）→ 埋点 `order_create` → 跳订单详情。
- 联系卖家：拼装 chat 参数（`convId=C{itemId}`、`peerId`、`itemId`、`itemTitle`、`itemImg`、`itemPrice`）→ `navigateTo chat`。
- 收藏：`toggleFav` → 调真实收藏接口（新增）→ Toast。
- 分享：`onShareAppMessage` 配置标题/路径；补充按钮触发 `wx.showShareMenu`。

### 3.4 异常与边界
| 场景 | 处理 |
|------|------|
| 商品不存在（ITEM_NOT_FOUND） | empty 态："商品不存在或已下架" |
| 商品下架/非在售 | 禁用"立即购买"，提示"商品当前不可购买" |
| 库存为 0 | 按钮置灰"已售罄" |
| 未登录（401） | request.js 自动跳登录页，登录后回跳 |
| 无收货地址 | 弹窗引导去 address 页新增 |
| 下单并发超卖 | 后端乐观锁 @Version + lockStock 校验；前端提示"手慢了，库存不足" |
| 图片加载失败 | 兜底占位图 |

## 4. 实现方案
### 4.1 前端模块拆分
- `item-detail.js`：状态（item/loading/fav/curImg）、生命周期、各 handler、埋点。
- `item-detail.wxml`：图集+视频、价格区、信息区、卖家区、描述区、推荐区、操作栏、empty 态。
- `item-detail.wxss`：沿用设计 token（主色 #FFB300、背景 var(--bg)、次要文字 var(--t2)/var(--t3)）。
- `api.js`：新增 `favorite` / `toggleFavorite`；复用 `getItemDetail` / `createOrder` / `getAddresses`。
- 新增推荐区块：调用 `search`（同 categoryId）获取相似商品。

### 4.2 接口契约
`GET /api/item/detail/{id}` → 200
```json
{
  "code": 0,
  "data": {
    "id": 9001,
    "sellerId": 1001,
    "sellerName": "数码小哥",
    "sellerAvatar": "https://...",
    "categoryId": 42,
    "categoryName": "游戏",
    "title": "Switch OLED 续航版 95新",
    "cover": "https://...",
    "price": 138000,
    "originalPrice": 209900,
    "conditionLevel": 3,
    "status": "onsale",
    "auditStatus": "pass",
    "city": "北京",
    "freight": 0,
    "viewCount": 326,
    "likeCount": 0,
    "favCount": 18,
    "createdAt": "2026-09-21 08:00:00",
    "description": "自用几个月...",
    "images": ["https://...", "https://..."],
    "videoUrl": null,
    "stock": 1
  }
}
```
金额单位：分。前端统一 `util.formatPrice(price/100)`。

新增收藏接口（建议）：
- `POST /api/item/{id}/favorite` 收藏
- `DELETE /api/item/{id}/favorite` 取消
- `GET /api/item/{id}/favorite/check` 查询是否已收藏
（当前 `ItemController` 无收藏端点，需后端补充。）

### 4.3 后端需配合的改动
1. `ItemVO` / `ItemDetailVO` 增加 seller 子对象（id、creditScore、realNameVerified），满足前端卖家卡片展示；或在 detail 接口聚合。
2. 新增收藏（favorite）接口与用户收藏表 `t_favorite`。
3. 相似推荐：detail 接口附带 `similarItems`（基于 categoryId + price 区间），或在 search 中按 categoryId 查询。
4. 金额/状态/库存字段与前端约定一致；status 文案映射由前端 `statusText` 维护（item: onsale→在售 等）。

### 4.4 状态与埋点
- fav 状态：进入页面 `GET /favorite/check` 初始化；toggle 时乐观更新 + 接口回滚。
- 埋点：`item_view`（onLoad）、`order_create`（buyNow 成功）、`share`（onShareAppMessage）。

## 5. 验收标准
- 详情页信息完整，金额以元显示、划线原价仅存在时展示。
- 不存在/下架/售罄给出正确空态或禁用态。
- 立即购买无地址时引导新增，下单成功跳订单详情并带正确 orderNo。
- 联系卖家正确携带商品上下文进入 IM。
- 收藏状态持久化（刷新后保持）。
- 未登录触发自动登录跳转并回跳。
- 分享卡片标题/路径正确。
- 真机/模拟器联调 `GET /api/item/detail/{id}` 返回字段与映射一致。

## 6. 风险与待办
- 前端卖家卡片依赖 seller 子对象，需后端先补字段（阻塞项）。
- 收藏功能后端端点缺失，需排期。
- 视频、相似推荐、卖家主页为 P2，可后续迭代。
- 金额单位「分」需全链路统一，避免前端直接展示大整数。
