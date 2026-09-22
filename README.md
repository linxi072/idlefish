# 闲置集 · C2C 二手交易平台

基于《系统架构说明书 V2.0》《PRD 首版》《页面原型图 V1.0》落地的三端工程。

| 工程 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| 后端 | `idlefish-backend/` | Spring Boot 3.3 + Java 17 + MyBatis-Plus + H2 + WebSocket | 已可运行，核心交易闭环（登录→发布→搜索→下单→支付→发货→确认收货→结算）+ IM + 风控埋点 + 运营后台接口 |
| 微信小程序 | `miniprogram/` | 原生小程序（WXML/WXSS/JS） | 消费者端：首页/分类/详情/发布/订单/消息/聊天/我的/地址/登录 |
| PC 运营后台 | `pc-admin/` | Vue 3 + Element Plus（CDN 免构建） | 运营侧：控制台/商品审核/订单管理/用户管理/类目管理/风控审计 |

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

## 备注
- 小程序 `tabBar` 当前为纯文字（未配图标）。若微信开发者工具要求图标，在 `miniprogram/images/` 放 PNG 并在 `app.json` 补 `iconPath` / `selectedIconPath` 即可。
- 微信支付 / 微信登录 / 物流 / ES 检索等在后端均以接口抽象 + Mock 实现，联调真实渠道时替换对应 `Real*ServiceImpl` 与配置开关即可。
