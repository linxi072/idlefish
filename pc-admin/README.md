# 闲置集 · PC 运营后台

Vue 3 + Element Plus **单文件组件（SFC）**，基于 **Vite** 构建的运营控制台，对齐 PRD 中「运营后台框架 / 四大管理模块」。
> 历史版本为 CDN 免构建架构（Vue/Element Plus/axios/ECharts 走 jsDelivr UMD），已于 V1.0.4 迁移至 Vite 单文件组件工程：页面拆分为 `.vue`，接口层按域拆分为 `api/modules/*.api.js`，公共样式外置为 `src/styles/global.css`。

## 本地运行（Vite）
```bash
cd pc-admin
npm install            # 安装 vue/element-plus/axios/echarts + vite/@vitejs/plugin-vue
npm run dev            # 开发服务器，默认 http://localhost:8787
# 或生产构建：npm run build && npm run preview
```
默认 `USE_MOCK=true`，无需后端即可体验；演示账号 `admin / admin123`。
（旧 `python3 -m http.server` 静态服务已弃用；`start.sh` 现已改为 `npx vite`。）

## 目录结构
```
pc-admin/
├── index.html               入口（仅保留 #app 与 /src/main.js；Vue/Element Plus/ECharts 由 npm 注入）
├── package.json             Vite 工程依赖与脚本（dev / build / preview）
├── vite.config.js           Vite 配置：plugin-vue + @→src 别名 + /api 代理 localhost:8080
├── start.sh                 启动脚本（npx vite，端口 8787）
└── src/
    ├── main.js              入口：装配 Vue + ElementPlus + ECharts + 路由
    ├── App.vue              根组件：登录门禁 + 侧边栏布局 + <router-view>
    ├── router/index.js      路由表（菜单 key 与 route name 一一对应）
    ├── api/                 接口层（按域拆分）
    │   ├── core.js          公共内核：USE_MOCK / BASE / 令牌 / 统一 axios + req / pageTo
    │   ├── index.js         聚合出口（桶文件）：视图统一 from '@/api'
    │   └── modules/*.api.js 各域接口（admin/system/wallet/attribute/notify/coupon/analytics/
    │                          marketing/member/dispute/review/activity/invite/recommend/search-term）
    ├── mock/index.js        本地 Mock 数据（USE_MOCK=true 时启用）
    ├── utils/
    │   ├── format.js        formatMixin + notifyError 等公共方法
    │   └── csv.js           toCsv / downloadCsv（与接口层解耦）
    ├── mixins/              listPageMixin / crudDialogMixin（选项式通用 mixin）
    ├── styles/global.css    全局样式（由旧 index.html <style> 外置）
    └── views/               业务页面（按模块拆分的 .vue 单文件组件）
        ├── auth/Login.vue
        ├── dashboard/Dashboard.vue
        ├── merchandise/{Items,Review}.vue
        ├── trade/{Orders,Dispute}.vue
        ├── user/Users.vue
        ├── catalog/{Categories,Attributes}.vue
        ├── risk/Risk.vue
        ├── analytics/Analytics.vue
        ├── marketing/{Marketing,Member,Recommend,Invite,SearchTerm,Coupon,Activity}.vue
        ├── finance/Wallet.vue
        ├── notify/Notify.vue
        └── system/{SysUser,Role,Organization,Menu,Dict}.vue
```

## 接口层说明
- 视图统一 `import { adminApi, walletApi, ... } from '@/api'`，无需关心底层按域拆分。
- `USE_MOCK` 开关优先级：URL `?mock=0/1` > `index.html` 的 `<meta name="use-mock">` > 缺省 `true`。
- `BASE` 接口基址读取 `index.html` 的 `<meta name="api-base">`，缺省 `http://localhost:8080`；联调真实后端时改 meta 或访问 `?mock=0`。
- `/api` 在 `vite.config.js` 中已代理到 `http://localhost:8080`，避免跨域。
- 图表库：视图内仍通过 `window.echarts` 引用（保留免构建期「无 echarts 降级」逻辑），由 `main.js` 注入 npm 版 ECharts。

## 联调真实后端
将 `index.html` 的 `<meta name="use-mock" content="true">` 改为 `false`（或访问 `index.html?mock=0`），并确保后端 `idlefish-backend` 运行于 8080（或改写 `api-base`）。
后端对应接口见 `idlefish-backend` 的 `AdminController` 等。

## 权限
演示为 RBAC 简化版（运营/风控/管理员）。真实接入时由后端 `X-Admin-Role` 头与接口鉴权控制。

## 系统管理（F-16 前端闭环）
左侧「系统管理」分组下五大模块，与后端 RBAC 系统管理一一对应：

| 菜单 | 前端视图 | 后端接口 |
| --- | --- | --- |
| 管理员 | `views/system/SysUser.vue` | `/api/admin/system/user` |
| 角色管理 | `views/system/Role.vue` | `/api/admin/system/role` + `assign-menus` |
| 机构管理 | `views/system/Organization.vue` | `/api/admin/system/organization` |
| 菜单管理 | `views/system/Menu.vue` | `/api/admin/system/menu` |
| 数据字典 | `views/system/Dict.vue` | `/api/admin/system/dict`（types/data/dropdown） |

所有增删改弹窗均使用 `el-form :rules` 规范化表单校验；树形模块（机构/菜单）支持新增子节点与递归删除保护；角色-菜单授权复用 `el-tree` 勾选。

## 运营模块（F-PC-02）
在「财务与运营」分组与顶栏铃铛补齐后端已落地但尚未接入的运营能力：

| 入口 | 前端视图 | 后端接口 | 说明 |
| --- | --- | --- | --- |
| 钱包/提现 | `views/finance/Wallet.vue` | `/api/admin/withdrawals` + `/withdrawals/{id}/approve`、`/reject`、`/reconciliation` | 提现申请列表 + 通过/驳回（二次确认、已处理禁重复）+ 资金对账报表 |
| 属性模板 | `views/catalog/Attributes.vue` | `/api/admin/attr-templates?categoryId=`、`/attr-template` | 级联选择叶子类目 → 查看/新增属性模板 |
| 消息中心 | `views/notify/Notify.vue` | `/api/notify/list`、`/unread-count`、`/read`、`/read-all` | 站内信列表（分页）+ 未读角标 + 标记已读/全部已读 |

**边界与异常处理**：
- 提现已处理（`approved`/`rejected`/`done`）的申请，通过/驳回按钮自动禁用，杜绝重复审批；
- 对账日期留空默认查昨日；真实模式按后端返回展示，字段缺失时均有安全兜底；
- 属性模板必须选择叶子类目方可新增；名称必填、排序须为数字（`el-form :rules`）；
- 消息中心真实模式按当前登录用户隔离查询个人站内信（若生产需查看全站通知，需后端补充管理员通知查询端点）。

## 启动
```bash
cd pc-admin
./start.sh            # 默认 8787；也可 ./start.sh 9000 指定端口
# 等价：npx vite --port 8787
# 浏览器打开 http://localhost:8787
```
> 必须以 HTTP 服务方式打开（Vite dev server 已满足）。默认 `USE_MOCK=true`，无需后端即可完整体验系统管理全链路。
