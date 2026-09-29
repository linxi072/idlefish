# 闲置集 · C2C 二手交易平台

基于《系统架构说明书 V2.0》《PRD 首版》《页面原型图 V1.0》落地的三端工程。

| 工程 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| 后端 | `idlefish-backend/` | Spring Boot 3.3 + Java 17 + MyBatis-Plus + MySQL + Redis + WebSocket | 核心交易闭环（登录→发布→搜索→下单→支付→发货→确认收货→结算）+ IM + 风控 + 评价信用 + 退款钱包；营销 2.0（积分 / 会员等级 / 拼团秒杀 / 邀请拉新 / 营销驾驶舱 F-13）+ 发现与体验（ES 检索 / 个性化推荐 / 搜索词运营 / 物流轨迹 / 订阅消息 / 体验细节 F-14）+ 风控治理（规则库 / Redis 限流 / 容器化 / 测试体系 / 运营 BI F-15）+ 售后维权工单（F-17） |
| 微信小程序 | `miniprogram/` | 原生小程序（WXML/WXSS/JS） | 消费者端：首页/分类/详情/发布（草稿自动保存 + 图片压缩重试）/订单/消息/聊天（IM 实时）/我的/地址/登录/维权（发起·我的·详情 F-17） |
| PC 运营后台 | `pc-admin/` | Vue 3 + Element Plus（CDN 免构建） | 运营侧：控制台/商品审核/订单管理（含物流轨迹）/用户管理/类目管理/风控规则库/会员等级/营销驾驶舱/运营 BI/维权工单（F-17） |

## 设计语言
对齐《页面原型图 V1.0》的闲鱼风视觉：
- 品牌主色 `#FFCE3D → #FFB300`（琥珀黄渐变）
- 背景 `#F4F5F7`，正文 `#1A1A1A` / `#666` / `#9A9A9A`，警示红 `#FF3939`

## 快速体验（Mock 模式，无需后端）
- **小程序**：用微信开发者工具「导入项目」选择 `miniprogram/`，`app.js` 中 `useMock: true` 默认开启，所有接口走本地 `utils/mock.js`，可直接预览全流程。
- **PC 后台**：在 `pc-admin/` 下起任意静态服务器（如 `python3 -m http.server 8787`）后浏览器打开 `index.html`，默认 Mock 模式，账号已预填（admin / admin123）。

## 联调真实后端
1. 启动 `idlefish-backend`（Spring Boot，默认 `http://localhost:8080`）。
2. 小程序：将 `miniprogram/app.js` 的 `useMock` 改为 `false`，并按需修改 `apiBaseUrl`（真机用电脑局域网 IP）。
3. PC 后台：将 `pc-admin/src/api.js` 的 `USE_MOCK` 改为 `false`，`BASE` 指向后端地址。
4. 后端接口契约见 `idlefish-backend` 各 `*Controller`（统一响应 `{code,msg,data,traceId}`，JWT Bearer 鉴权）。

## 状态机（与 PRD 一致）
- 商品 7 态：`draft / pending_review / onsale / locked / sold / rejected / off_shelf`
- 订单 6 态：`pending_pay / paid / pending_ship / shipping / completed / closed`
- 退款 7 态：`apply / wait_seller / platform / refunding / refunded / rejected / canceled`
- 维权工单（F-17）状态机：`PENDING → SELLER_REPLIED → PLATFORM → RESOLVED → CLOSED`；旁路 `PENDING / SELLER_REPLIED → CANCELED`；裁决仅落库「结果与退款金额」，实际退款交既有退款链路（F-07/F-08）执行，避免重复出款资损

## 配套文档

| 文档 | 内容 |
| --- | --- |
| `docs/真机联调验证手册.md` | IM 实时/支付回流/优惠券营销三域的联调验收口径、数据库一致性 SQL、护栏与一键命令 |
| `docs/运维部署手册.md` | 生产构建、环境变量总表、MySQL/Redis 初始化、后端/小程序/PC 后台部署、外部组件真实接入、监控/对账/限流、备份回滚与排障 |
| `docs/后续功能迭代路线图.md` | 功能规划、优先级与实施进度台账（F-01~F-17） |
| `docs/测试体系说明.md` | 六层测试体系（纯函数单测 / 契约一致性 / 集成 / 冒烟 / 压测 / 小程序 e2e）与「契约即文档」约定 |
| `docs/P0功能进度清单.md` | ⚠️ 历史快照（2026-09-23 · V1.0.2）：26 个 P0 模块基线，最新进度见路线图 |

## 备注
- 小程序 `tabBar` 当前为纯文字（未配图标）。若微信开发者工具要求图标，在 `miniprogram/images/` 放 PNG 并在 `app.json` 补 `iconPath` / `selectedIconPath` 即可。
- 外部组件（微信支付 / 登录、OSS、ES 检索、RocketMQ 延时、物流、内容安全、短信）已统一为**真实实现**（`Real*ServiceImpl` 为唯一 Bean，无 Mock 开关，8 个 Mock/Local 回退实现已移除）；凭据经环境变量（`IDLEFISH_*`）注入，缺凭据时对应组件 fail-closed 不启用，详见 `docs/运维部署手册.md` §8。
