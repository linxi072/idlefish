# 闲置集 C2C 二手交易平台 · 项目全面梳理与开发计划

> 版本：v1.0 ｜ 生成日期：2026-09-21 ｜ 范围：后端 `idlefish-backend`（111 Java）、小程序 `miniprogram`（56 文件）、PC 运营后台 `pc-admin`（13 文件）
>
> 结论先行：**当前三端在 Mock 模式下可跑通演示，但切换到真实 Spring Boot 后端联调时，存在 3 个 P0 级资金/安全风险与 1 个 P0 级金额单位资损风险，以及若干 P1 级契约不一致与功能缺失。** 建议按"资金安全 → 联调一致性 → 质量体验"三阶段推进。

---

## 0. 项目现状概览

| 端 | 技术栈 | 文件数 | 当前状态 | 主要遗留 |
|---|---|---|---|---|
| 后端 | Spring Boot 3.3.4 / Java 17 / MyBatis-Plus 3.5.7 / H2 | 111 | 可编译启动（0 ERROR） | 外部依赖全 Mock；零测试；3 个 P0 安全/资损隐患 |
| 小程序 | 原生微信小程序 | 56 | 11 页面 + utils | 金额单位未转换；IM 未接 WebSocket；图片上传未实现 |
| PC 后台 | Vue3 + Element Plus（CDN 免构建） | 13 | 7 视图 | 发货/退款接口缺失；无响应拦截；无分页；无路由守卫 |

**核心约束**：项目盘目前仅 1 名成员（owner「曲终人散」），评审人与开发角色无法分派给不同人；沙箱对 localhost 跨进程隔离 + 代理 502，真机端到端 curl 联调需在本机/连接器执行。

---

## 1. 问题清单（缺陷 / 技术债务 / 开发堵塞项）

> 严重程度：P0 = 阻塞业务闭环或重大安全/资金风险；P1 = 重要功能缺失或一致性问题影响联调；P2 = 可优化的技术债务。
> 责任归属以**模块/角色**标注（当前由单人承担，规划中按角色拆分）。

### 1.1 P0（必须最先修复，涉及资金与越权）

| 编号 | 问题 | 类型 | 影响范围 | 责任归属 | 证据 |
|---|---|---|---|---|---|
| D-01 | **管理后台越权 / 权限提升**：角色取自客户端 Header `X-Admin-Role`，无任何服务端管理员身份认证，`/api/admin/**` 仅需普通用户 JWT | 安全缺陷 | 全部运营接口（封禁、审核、查订单/用户） | 后端·admin 模块 | `AdminController.java:29/44/77`、`AdminAuthService.java:33-38`、`WebConfig.java` 未排除 `/api/admin/**` |
| D-02 | **乐观锁未生效 + 确认收货双重结算（资损）**：未注册 `OptimisticLockerInnerInterceptor`，`@Version` 形同虚设；`confirmReceive`/`autoConfirmReceive` 在 10 天边界并发时各自生成结算单与资金流水 IN | 资金缺陷 | 交易资金链路 | 后端·trade/common.config | `MybatisPlusConfig.java:13-21`、`OrderService.java:140-151/193-206`、`SettlementService.java:36-52` |
| D-03 | **支付回调无签名校验（生产致命）**：`/api/pay/notify` 直接以入参标记已支付，真实对接时任何人可伪造支付成功 | 安全缺陷 | 支付链路 | 后端·pay 模块 | `WebConfig.java:34` 放行、`PayController.java:31-35`、`PayService.notify` |
| D-04 | **金额单位未统一（分↔元）**：前端 `formatPrice` 无 `/100`，发布页以"元"直传后端被当"分"，造成 100 倍价差；Mock 数据本身是"元"掩盖缺陷 | 契约缺陷/资损 | 全站价格展示与下单 | 前端（小程序/PC）+ 后端 VO | `miniprogram/utils/util.js:2-6`、`publish.js:60`、`pc-admin/mock.js:22/32`、后端 VO 未做元转换 |

### 1.2 P1（联调前必须解决）

| 编号 | 问题 | 类型 | 影响范围 | 责任归属 | 证据 |
|---|---|---|---|---|---|
| D-05 | **ItemDetailVO 契约不一致**：前端读 `seller.{id,creditScore,avatar}`，后端仅 `sellerName/sellerAvatar`；且 `User` 实体缺 `realNameVerified` 字段 | 契约缺陷 | 商品详情页 | 后端·item + 小程序 | `ItemDetailVO.java:13-19`、`User.java:14-34`、`item-detail.wxml:27/29/30/32` |
| D-06 | **收藏功能缺失**：无 `/favorite` 端点、无 `t_favorite` 表 | 功能缺失 | 商品详情收藏 | 后端·item + 小程序 | grep 全工程无 favorite 端点；`schema-mysql.sql` 无表 |
| D-07 | **Mock 支付完成端点不存在**：`MockWechatEscrowServiceImpl` 提示调 `/api/pay/mock/{payNo}`，但无对应 Controller | 开发堵塞 | 小程序 mock 支付 | 后端·pay | 仅字符串出现，无 Controller |
| D-08 | **`t_item` 缺 `version` 列**：实体声明 `@Version` 但 DDL 无列，启用乐观锁即运行时报错 | 技术债务 | 商品状态流转 | 后端·schema | `Item.java:37-38`、`schema-mysql.sql:46-69` |
| D-09 | **订单详情字段/运费不一致**：`order/detail` 依赖 `order.item.seller`/`buyerId`，运费恒写 0 未用 `order.freight` | 契约缺陷 | 订单详情页 | 小程序 + 后端·trade | `order/detail.js:86-87/26`、`detail.wxml:16/18/26` |
| D-10 | **小程序 Token 无自动续期**：`refreshToken` 已定义却零调用，过期仅强跳登录且不清理 token | 体验缺陷 | 全小程序登录态 | 小程序·utils | `request.js:24-27`、`api.js:20`、`store.js` |
| D-11 | **小程序图片发布失效**：`publish` 直接 POST 本地临时路径，未 `wx.uploadFile` | 功能缺陷 | 发布闲置 | 小程序·publish | `publish.js:36-44/62` |
| D-12 | **小程序 IM 未接 WebSocket**：`chat` 页无 `wx.connectSocket`，仅拉一次历史、发送走 HTTP，无实时 | 功能缺陷 | 聊天 | 小程序·chat + 后端·im | `chat.js` 无 connectSocket；`meId` 硬编码 2001 |
| D-13 | **小程序支付无轮询/倒计时**：提示"30 分钟超时"但无定时器、无订单状态轮询 | 体验缺陷 | 支付回流 | 小程序·item-detail | `detail.js:26-44`、`detail.wxml:6` |
| D-14 | **PC 发货/退款接口缺失**：`orders.js` 仅弹"（演示）"，未调任何 API | 功能缺失 | 订单管理 | PC·orders + 后端·trade | `pc-admin/api.js` 无 shipOrder/refund；`orders.js:34/37` |
| D-15 | **PC 路径/状态语义错误**：`/api/admin/item` 应为 `/items`；发货按钮 `v-if="status==='shipping'"`（待收货）应为 `pending_ship`（待发货） | 契约缺陷 | 商品/订单审核 | PC·api/orders | `api.js:28/31/33`、`orders.js:5/66` |
| D-16 | **PC 无响应拦截/401 处理**：`res.data.data` 强假设、无 code 校验、无 401 登出 | 健壮性问题 | 全 PC 请求 | PC·api/main | `api.js:19`、`main.js:15` |
| D-17 | **PC 无服务端分页/筛选**：全量拉取 + 前端 filter，大数据量不可用 | 性能缺陷 | 列表页 | PC + 后端 | `items.js:13`、`orders.js:20`、`users.js:10` |
| D-18 | **PC 登录态仅校验字符串存在**：无路由守卫、无 token 有效性校验 | 安全缺陷 | 后台入口 | PC·main | `main.js:15` |

### 1.3 P2（质量与体验打磨）

| 编号 | 问题 | 类型 | 影响范围 | 责任归属 | 证据 |
|---|---|---|---|---|---|
| D-19 | **零测试**：`src/test` 无任何文件 | 技术债务 | 质量保障 | 后端 | 目录为空 |
| D-20 | **校验/异常覆盖不全**：`@RequestBody` 缺 `@Valid`；`GlobalExceptionHandler` 漏 `HttpMessageNotReadable`/`ConstraintViolation`/`NoResourceFound` | 健壮性 | 全后端 | 后端·common | `ItemController.publish:30`、`GlobalExceptionHandler.java:19-40` |
| D-21 | **配置/安全债务**：端口硬编码 8080、JWT secret 明文、`data-mysql.sql` 管理员明文 `admin123`、`idlefish.pay.mock` 缺省 true（误部署即全量 mock）、未配逻辑删除（`delete` 实置 `OFF_SHELF` 无 `deleted` 列） | 安全/配置 | 部署安全 | 后端·config | `application.yml:2/32`、`data-mysql.sql:33` |
| D-22 | **`@CurrentUser` 未判空**：公开接口误用会 NPE | 健壮性 | 公共接口 | 后端·common | `CurrentUserArgumentResolver.java:31-34` |
| D-23 | **小程序静默吞错 + 无下拉刷新**：大量 `.catch(()=>{})`，无重试态 | 体验 | 全小程序 | 小程序 | `index.js:17/37` 等 |
| D-24 | **小程序 mine 死链 / tabBar 图标风险**：菜单 `url:''` 仅 toast；tabBar 无图标（现可编译，补图标忘放 PNG 会失败） | 体验 | 我的页 | 小程序·mine/app.json | `mine.js:8-14`、`app.json:27-32` |
| D-25 | **PC 看板图表为手写 CSS bar**（无 ECharts）、商品无详情弹窗、表单校验缺位 | 体验 | 看板/商品 | PC·dashboard/items | `dashboard.js:34-40`、`index.html` 无图表 CDN |
| D-26 | **PC CDN 免构建生产风险**：全依赖外网 jsdelivr、无 SRI、无离线兜底、无打包压缩 | 部署风险 | 生产可用 | PC·index.html | `index.html:8-11` |
| D-27 | **PC/JWT 存储于 localStorage（XSS 易窃取）**、演示账号预填、BASE 硬编码 | 安全 | 后台安全 | PC | `api.js:5/8-10`、`login.js:8` |

---

## 2. 业务目标与需求清单

### 业务目标（BG）
- **BG-1 消费者交易闭环**：小程序可完成 登录→发布→搜索→下单→支付→发货→确认收货→评价。
- **BG-2 运营管理闭环**：PC 后台可完成 商品审核、订单发货/退款、用户管理、类目管理、风控查看。
- **BG-3 后端稳健可接真**：状态机/幂等/资金安全正确；外部渠道（支付/登录/物流/ES）可平滑切换真实实现。
- **BG-4 资金零差错**：无重复结算、无伪造回调、金额单位全链路一致。

### 需求清单（R）

| 编号 | 需求 | 功能描述 | 验收标准 | 依赖 | 优先级 |
|---|---|---|---|---|---|
| R-01 | 金额单位统一契约 | 全链路约定"分"为单位，VO 层统一提供元展示值；前端 `formatPrice` 做 `/100`，发布上传 `*100`；输出契约文档 | 任意价格展示正确（如 13800 分→¥138.00）；发布价无 100 倍偏差；三端文档一致 | 无 | P0 |
| R-02 | 管理后台独立鉴权 | 管理员独立登录态/JWT 签发；服务端按登录主体查角色权限；禁止客户端传角色；`/api/admin/**` 强制鉴权 | 非管理员调用 admin 接口返回 403；角色权限按服务端配置生效 | 无 | P0 |
| R-03 | 乐观锁 + 结算幂等 | 注册 `OptimisticLockerInnerInterceptor`；状态转移用 `update().eq(status)` 做 CAS；`onTradeSuccess` 按 orderNo 幂等 | 并发确认收货仅生成 1 张结算单、1 条资金 IN；版本冲突抛 `STATE_NOT_ALLOWED` | D-02/D-08 | P0 |
| R-04 | 支付回调验签 | 接入微信回调签名/证书校验；校验订单金额一致性；仅信任验签通过的通知 | 伪造/篡改的回调被拒绝；金额不符拒绝 | D-03 | P0 |
| R-05 | 商品详情 seller 契约对齐 | 后端新增 `SellerVO{id,creditScore,realNameVerified,avatar}` 并补齐 `User.realNameVerified`；详情返回 seller 子对象 | 小程序 `item.seller.*` 字段全部有值；前后字段一致 | D-05 | P1 |
| R-06 | 收藏功能补齐 | 新增 `t_favorite` 表 + 增删查/收藏列表/是否收藏标记端点；小程序接入真实收藏 | 收藏/取消即时生效；列表正确标记已收藏 | D-06 | P1 |
| R-07 | 补齐 Mock 支付完成端点 | 新增 `PayController.mockPay(payNo)` 或文档改为用 `/notify` 触发 | 小程序 mock 支付可走通 | D-07 | P1 |
| R-08 | 小程序登录自动续期 | `request.js` 401 分支接入 `refreshToken` 重试一次；过期跳转前 `clearToken` | token 过期无感续期；不再强制重新微信登录 | D-10 | P1 |
| R-09 | 小程序图片上传 | `publish` 先 `wx.uploadFile` 逐张拿 URL 再提交 | 发布商品图片真实可显示 | D-11 | P1 |
| R-10 | 小程序 IM 实时 | `chat` 接入 `wx.connectSocket`；消息实时收发；`meId` 取真实 `userInfo.id` | 发送后对方实时收到；列表自动刷新 | D-12 | P1 |
| R-11 | 小程序支付回流 | 订单详情/列表加 `setInterval` 轮询 `getOrder` 或订阅支付结果；下单页倒计时 | 30 分钟超时自动关闭可见；支付成功后状态回流 | D-13 | P1 |
| R-12 | 订单详情字段/运费对齐 | 统一订单快照字段（`itemTitle/itemImg/itemPrice/buyerId/freight`）；运费真实渲染 | 订单详情卖家/买家/运费均正确 | D-09 | P1 |
| R-13 | PC 发货/退款接口 | 补齐 `shipOrder(orderNo,company,no)`、`refund(orderNo,agree)` 对接 `RefundController`；按钮真实调用 | 发货/同意退款在后端落库并变更状态 | D-14 | P1 |
| R-14 | PC 契约校准 | 路径改复数 `/items`/`/users`/`/categories`；发货按钮触发条件改 `pending_ship`；对齐状态枚举 | 审核/封禁/发货按钮可点且生效 | D-15 | P1 |
| R-15 | PC 响应拦截 + 401 | `api.js` 增加响应拦截器，按 code 抛错、401 清 token 回登录；统一 `ElMessage` | 业务错误有提示；401 自动登出 | D-16/D-18 | P1 |
| R-16 | PC 服务端分页/筛选 | 列表传 `page/pageSize/keyword/status`；后端实现分页查询 | 大数据量列表可翻页、可搜索 | D-17 | P1 |
| R-17 | t_item version / 逻辑删除补全 | 补 `t_item.version INT DEFAULT 0`；评估并配置逻辑删除列 | 乐观锁与软删除行为符合预期 | D-08/D-21 | P1 |
| R-18 | 单元测试/集成测试 | 核心交易链路（下单幂等、状态机、结算幂等、退款自动同意）加测试 | 关键路径有测试覆盖，CI 可跑 | 无 | P1 |
| R-19 | 异常/校验覆盖 | `@RequestBody` 加 `@Valid`；补全 `GlobalExceptionHandler` 异常类型 | 参数错误返回 400 而非 500 | D-20 | P2 |
| R-20 | 配置安全治理 | JWT secret 外置、管理员密码加密、端口 `${PORT:8080}`、`idlefish.pay.mock` 默认 false 或显式开关 | 误部署不会全量 mock；密钥不落地明文 | D-21/D-27 | P2 |
| R-21 | PC 看板图表 + 商品详情 | 引入 ECharts CDN 实现趋势图；商品审核加详情弹窗（大图/描述/资质） | 看板有真实图表；审核可见详情 | D-25 | P2 |
| R-22 | 小程序体验打磨 | 错误 toast 兜底 + 重试态；mine 菜单接通；tabBar 补图标 | 无静默吞错；菜单可跳转 | D-23/D-24 | P2 |
| R-23 | PC 生产化 | CDN 改本地打包 + SRI/离线兜底；JWT 改 httpOnly Cookie | 断网/CDN 故障仍可运行；XSS 难窃取 token | D-26/D-27 | P2 |

---

## 3. 统一优先级排序（问题 ↔ 需求合并视图）

| 优先级 | 问题（D） | 对应修复需求（R） | 阶段 |
|---|---|---|---|
| **P0** | D-01 后台越权 | R-02 | 阶段一 |
| **P0** | D-02 双重结算 | R-03 | 阶段一 |
| **P0** | D-03 支付无验签 | R-04 | 阶段一 |
| **P0** | D-04 金额单位 | R-01 | 阶段一 |
| **P1** | D-05 seller 契约 | R-05 | 阶段二 |
| **P1** | D-06 收藏缺失 | R-06 | 阶段二 |
| **P1** | D-07 mock 支付端点 | R-07 | 阶段二 |
| **P1** | D-08/17 t_item version/分页 | R-17/R-16 | 阶段二 |
| **P1** | D-09 订单字段/运费 | R-12 | 阶段二 |
| **P1** | D-10 token 续期 | R-08 | 阶段二 |
| **P1** | D-11 图片上传 | R-09 | 阶段二 |
| **P1** | D-12 IM WebSocket | R-10 | 阶段二 |
| **P1** | D-13 支付回流 | R-11 | 阶段二 |
| **P1** | D-14/15 PC 发货退款/契约 | R-13/R-14 | 阶段二 |
| **P1** | D-16/18 PC 拦截/守卫 | R-15 | 阶段二 |
| **P1** | D-19 零测试 | R-18 | 阶段二/三 |
| **P2** | D-20 校验异常 | R-19 | 阶段三 |
| **P2** | D-21/D-27 配置安全 | R-20/R-23 | 阶段三 |
| **P2** | D-22 @CurrentUser 判空 | R-19 | 阶段三 |
| **P2** | D-23/D-24 小程序体验 | R-22 | 阶段三 |
| **P2** | D-25 PC 图表/详情 | R-21 | 阶段三 |
| **P2** | D-26 CDN 生产化 | R-23 | 阶段三 |

---

## 4. 分阶段开发计划

> 排期为相对周（W1–W8），假设单人全职；若增派人手可并行压缩。负责模块按角色标注（当前单人承担，建议后续按角色拆分：后端开发 / 小程序开发 / PC 前端开发 / 架构评审）。

### 阶段一 · 资金安全与越权加固（W1–W2，P0）
- **目标**：消除资损与越权风险，确立金额单位契约，使真实后端具备"可安全联调"基础。
- **负责模块**：后端·admin / trade / pay / common；前端（小程序+PC）金额改造。
- **落地路径**：
  1. R-01 输出《金额单位契约文档》，后端 VO 增加元展示字段，前端 `formatPrice` 改 `/100`、发布 `*100`。
  2. R-02 新增管理员独立登录（AdminUser + 独立 JWT），`AdminAuthService` 改为按登录主体查权限，WebConfig 将 `/api/admin/**` 纳入鉴权。
  3. R-03 注册乐观锁拦截器；`OrderService` 状态转移改为 CAS；`SettlementService.onTradeSuccess` 加 orderNo 幂等。
  4. R-04 先以接口契约预留验签位（Mock 模式放行），真实对接时启用微信证书校验。
- **里程碑**：P0 全清，后台越权/双重结算/金额偏差三类问题关闭。

### 阶段二 · 联调一致性闭环（W3–W5，P1）
- **目标**：前后端契约对齐，交易与运营核心链路在真实后端下跑通。
- **负责模块**：后端·item/trade/im；小程序全页面；PC·api/orders/items。
- **落地路径**：
  1. R-05/R-12 后端补齐 `SellerVO` + `User.realNameVerified` + 订单快照字段；小程序 `item-detail`/`order/detail` 改为读取正确字段与运费。
  2. R-06/R-07 新增收藏表与端点、Mock 支付完成端点。
  3. R-08/R-09/R-10/R-11 小程序登录续期、图片上传、IM WebSocket、支付回流轮询。
  4. R-13/R-14/R-15/R-16/R-17 PC 发货退款接口、路径/状态校准、响应拦截与 401、服务端分页、`t_item` version。
- **里程碑**：三端均可在本机/连接器对真实后端完成登录→商品→下单→支付→发货→确认→结算与 PC 审核/发货/退款。

### 阶段三 · 质量与体验打磨（W6–W8，P2）
- **目标**：补齐测试、加固配置安全、优化两端体验与 PC 生产化。
- **负责模块**：后端·test/common/config；小程序体验；PC 图表/生产化。
- **落地路径**：
  1. R-18 核心交易链路单测/集成测试，搭建可跑 CI。
  2. R-19/R-20/R-23 校验异常补全、JWT/密码/端口/逻辑删除治理、JWT 存储改 Cookie。
  3. R-21 PC 引入 ECharts 看板、商品详情弹窗。
  4. R-22 小程序错误兜底、mine 菜单、tabBar 图标。
  5. R-23 PC CDN 转本地打包 + SRI/离线兜底。
- **里程碑**：测试覆盖关键路径；配置无明文密钥；PC 可离线运行；小程序无静默吞错。

---

## 5. 关键风险点与应对措施

| 风险 | 等级 | 触发场景 | 应对措施 |
|---|---|---|---|
| **RK-1 金额单位联调资损** | 高 | 切真实后端后前端未转换，订单金额错 100 倍 | 阶段一首要交付 R-01 契约文档 + 单测断言金额换算；联调前用脚本校验全链路金额 |
| **RK-2 双重结算资损** | 高 | 确认收货并发 | R-03 乐观锁 CAS + 结算幂等；压测模拟 10 天边界并发 |
| **RK-3 Mock 掩盖真实缺陷** | 中高 | 演示正常、上线即崩 | 阶段二强制在真实后端（H2 本地）跑通后再宣称完成；禁止以 Mock 通过验收 |
| **RK-4 单人项目无独立评审** | 中 | 评审与开发同一人，质量盲区 | 在事项系统显式记录 self-review 结论；建议项目增派成员或由连接器引入独立评审 |
| **RK-5 沙箱网络隔离致联调难** | 中 | localhost 跨进程不通、代理 502 | 真机联调统一在本机/连接器执行；后端锁 8080、单进程内启动→联调→关闭 |
| **RK-6 PC CDN 生产故障** | 中 | jsdelivr 不可达导致后台白屏 | R-23 转本地打包 + SRI/离线兜底；至少加 CDN 失败回退 |
| **RK-7 支付回调伪造** | 高（生产） | 误部署且未验签 | R-04 验签 + 金额校验；`idlefish.pay.mock` 默认 false，杜绝误开 |
| **RK-8 后台越权暴露** | 高 | 当前 Header 传角色即提权 | R-02 阶段一必做，上线前不可遗留 |

### 5.1 风险处置进展（已落地 · 2026-09-21）

| 风险 | 处置状态 | 落地要点（证据） |
|---|---|---|
| RK-1 金额单位联调资损 | ✅ 代码已落地 | 后端 `ItemVO`/`OrderVO` 增加 `*Yuan`（分→元）展示字段；小程序 `util.formatPrice` 改 `/100`、发布页 `*100` 上传；订单页 `o.amount` 兼容字段；后端 `mvn compile` **BUILD SUCCESS** |
| RK-2 双重结算资损 | ✅ 代码已落地 | 注册 `OptimisticLockerInnerInterceptor`；`OrderService` 状态转移改用 CAS + 影响行数校验（冲突抛 `STATE_NOT_ALLOWED`）；`SettlementService.onTradeSuccess` 按 `orderNo` 幂等 + `t_settlement.order_no` 唯一约束兜底 |
| RK-3 Mock 掩盖真实缺陷 | ✅ 脚本已落地 | `scripts/verify-backend.sh` 强制真实后端（H2 本地）编译 + 启动 + 冒烟后再验收；明确禁止以 Mock 演示替代 |
| RK-4 单人无独立评审 | ✅ 流程已落地 | 在事项系统评审事项 `rrBb0G` 显式记录 self-review 结论与已缓解风险清单（见 §6 / 事项评论） |
| RK-5 沙箱网络隔离致联调难 | ✅ 脚本已落地 | `scripts/run-backend.sh` 单进程锁 8080 启动→联调→关闭，规避 localhost 跨进程隔离 |
| RK-6 PC CDN 生产故障 | ✅ 代码已落地 | `pc-admin/index.html` 增加 CDN（Vue/ElementPlus）加载失败兜底提示，避免白屏 |
| RK-7 支付回调伪造 | ✅ 代码已落地 | `PayService.notify` 增加金额一致性校验（`BIZ_ERROR`）+ `escrow.verifyNotify` 验签骨架（`MockWechatEscrowServiceImpl` 通过；`RealWechatEscrowServiceImpl` 接证书后启用并安全失败）；`PayController` 增加 `amount` 入参 |
| RK-8 后台越权暴露 | ✅ 代码已落地 | 移除客户端 `X-Admin-Role` 提权通道；新增 `/api/admin/auth/login` 独立登录签发 admin JWT；`AdminAuthInterceptor` 校验 admin 标志；`WebConfig` 将 `/api/admin/**` 纳入独立鉴权，写操作审计落痕 `operatorId` |

#### 5.1.1 二次加固（2026-09-22 · 代码级补齐）

基于只读代码审计，对 4 项风险做进一步代码级闭环（已 `mvn -o test-compile` BUILD SUCCESS）：

| 风险 | 加固点 | 落地证据 |
|---|---|---|
| RK-2 双重结算资损 | `t_fund_flow.biz_no` 补 UNIQUE 约束（原仅普通索引，存在并发重复放款）；`SettlementService.processDue` 改为「`WHERE id AND status='pending'` CAS 认领 + `@Transactional` + 唯一约束兜底」，彻底杜绝双重放款 | `schema-mysql.sql:153` `biz_no VARCHAR(32) UNIQUE`；`SettlementService.java` `processDue` CAS `update(...).eq(Id).eq(Status,"pending")`，影响行数 0 即跳过 |
| RK-1 金额单位资损 | 补齐聊天场景价格口径：会话列表 `message.js` 原先传原始「分」值导致聊天卡 `¥{{itemPrice}}` 显示 100 倍；统一改为 `formatPrice` 元字符串（`item-detail.js` 已合规） | `miniprogram/pages/message/message.js:19` 改为 `formatPrice(c.item.price)` 并 `encodeURIComponent`；`chat.wxml:7` 直接展示元值 |
| RK-6 PC CDN 生产故障 | `index.html` 兜底由仅检测 Vue/ElementPlus 扩展为逐一检测 Vue/ElementPlus/axios/echarts，任一缺失即渲染友好面板并列出缺失项（含提示生产改本地打包 + SRI/离线） | `pc-admin/index.html` 底部兜底脚本重写为 `required[]` 遍历检测并渲染缺失清单 |
| RK-7 支付回调伪造 | `PayService.notify` 向 `escrow.verifyNotify` 传入完整回调上下文（payNo/transactionId/amount），为证书接入后的真实验签提供参数；真实路径仍 `RealWechatEscrowServiceImpl.verifyNotify` 显式失败关闭（fail-closed，伪造回调不可绕过） | `PayService.java` 构建 `notifyParams` Map 传入；`RealWechatEscrowServiceImpl.java:33` 仍抛 `UnsupportedOperationException` |

> 注：RK-3/RK-5（验证/联调脚本）、RK-4（单人自评，已在事项系统 `rrBb0G` 留痕）本轮未改动；RK-7 真实微信验签需平台证书 + APIv3 密钥，属上线前凭据接入项，当前以 fail-closed 兜底。

> **验收门槛**：后端已 `BUILD SUCCESS`（0 ERROR），8 项风险的代码/脚本/流程均已落地。端到端功能正确性仍须在本机/连接器对**真实后端（H2 本地）**联调验证（见 `scripts/verify-backend.sh`），沙箱 localhost 跨进程隔离无法在 Bash 内完成真机联调。

### 5.2 功能模块落地进展（已落地 · 2026-09-22）

基于"梳理边界→按规划实现→保持架构/命名一致→完整可运行"的路线，本轮补齐阶段二 P1 功能性缺失模块。后端 `mvn clean compile` **BUILD SUCCESS（123 文件，0 ERROR）**；小程序 8 个改动 JS 文件 `node --check` 全部通过。

| 需求 | 边界梳理结论 | 落地内容 | 验收状态 |
|---|---|---|---|
| R-05 seller 契约 | `ItemVO` 仅有扁平 `sellerName/sellerAvatar`，`item-detail.js`/`order-detail.js` 却用 `item.seller.*`，真实后端返回 `seller` 为 null 会崩溃 | 新增 `item/vo/SellerVO.java`；`ItemService.toVO` 在回填 `sellerName/sellerAvatar` 的同时构建并填充 `seller` 子对象（含 `realNameVerified`）；`User` 实体补 `realNameVerified` 字段；`schema-mysql.sql` 补 `t_user.real_name_verified` | ✅ 代码 + 编译 |
| R-06 收藏功能 | 无任何收藏模块/表 | 新增 `favorite` 包（entity/mapper/vo/service/controller）；`schema-mysql.sql` 新增 `t_favorite` 表 + `user_id` 索引；`FavoriteController` 提供 add/remove/check/list；小程序 `api.js` 接真实端点、`mock.js` 本地集合、`item-detail` 收藏态联动、新增 `pages/favorite` 收藏列表页并接入"我的"菜单 | ✅ 代码 + 编译 |
| R-07 Mock 支付完成 | 无 mock 完成端点，小程序 mock 支付无法走通 | `PayService.mockComplete`（仅 `idlefish.pay.mock=true` 可用，复用 `notify` 幂等/验签）；`PayController` 暴露 `POST /api/pay/mock/{payNo}`；`PayController.notify` 的 `transactionId` 改为可选以兼容演示；小程序 `order/detail.pay()` mock 分支改走 `payMockComplete` | ✅ 代码 + 编译 |
| R-09 图片上传 | `publish` 直接把本地临时路径当 URL 提交，图片不可显示 | 新增 `file` 包（`FileService` 落本地磁盘 + `FileController` 暴露 `POST /api/file/upload` 返回可访问 URL，仅登录用户）；小程序 `request.js` 封装 `http.upload`（`wx.uploadFile`）；`publish.js` 改为先逐张上传取 URL 再发布 | ✅ 代码 + 编译 |
| R-17 t_item version | `Item` 声明 `@Version` 但 `t_item` 无 `version` 列，且乐观锁拦截器已注册 → 每次商品更新运行时必失败 | `schema-mysql.sql` 补 `t_item.version INT DEFAULT 0`；`ItemService` 全量更新操作（发布审核/编辑/上下架/删除/锁库存/释放库存/状态转移）统一携带 `version` 做 CAS，冲突抛 `STATE_NOT_ALLOWED`（与订单状态机一致） | ✅ 代码 + 编译 |

> **一致性说明**：新增模块沿用既有约定——`Result<T>` / `Code` 统一响应、`@CurrentUser LoginUser` 取登录态、`BaseEntity` 审计字段、`fenToYuan` 分→元、包名 `com.idlefish.trade.*` 按业务垂直划分（favorite/file 与 item/trade/user 平级）。
>
> **仍待本机联调闭环（RK-3/RK-5 既定路径）**：沙箱 localhost 跨进程隔离，无法在 Bash 内跑通真实后端冒烟。需在**本机/连接器**执行 `scripts/verify-backend.sh`（启动 8080 → 登录 → 校验 `priceYuan` → 收藏/上传/mock 支付 → 关闭）。
>
> **已知真实模式缺口（本轮未纳入"剩余模块"清单，需后续排期）**：`OrderVO` 不含 `item` 子对象（仅从快照取 `title/cover`），`order-detail.js` 的 `o.item.seller`/`o.item.id` 在真实后端模式下为空（mock 模式正常）。建议后续在 `OrderService.toVO` 补充 `item` 轻量子对象（id/title/cover/sellerId）以闭环。

---

## 6. 立即行动建议（Next Steps）
1. **今天**：确认是否在本机/连接器执行阶段一，并指派 R-01/R-02/R-03/R-04 的责任人（建议角色拆分）。
2. **W1 启动**：先交付《金额单位契约文档》与后台独立鉴权，关闭 D-01/D-04。
3. **同步**：将本计划回填至项目事项系统，按 P0/P1/P2 建子任务并关联评审事项 `rrBb0G` 与研发事项 `rJyF65`。

> 备注：本文档基于三端源码实测审计，问题证据均标注具体文件/行号；需求验收标准以"真实后端（H2 本地）联调通过"为最终门槛，不以 Mock 演示通过替代。
