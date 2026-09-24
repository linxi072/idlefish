# 闲置集 · PC 运营后台

Vue 3 + Element Plus（CDN 免构建）的运营控制台，对齐 PRD 中「运营后台框架 / 四大管理模块」。

## 运行
```bash
cd pc-admin
python3 -m http.server 8787      # 或任意静态服务器
# 浏览器打开 http://localhost:8787
```
默认 `USE_MOCK=true`，无需后端即可体验；账号已预填（admin / admin123）。

## 目录结构
```
pc-admin/
├── index.html         入口（加载 Vue/Element Plus/axios CDN + 全局样式）
└── src/
    ├── main.js         App 根组件：侧边栏布局 + 视图切换 + 登录门禁
    ├── api.js          接口层（USE_MOCK 路由 mock / 真实后端，BASE 可配）
    ├── mock.js         本地 Mock 数据
    └── views/
        ├── login.js        登录页
        ├── dashboard.js    控制台（GMV/订单/用户/待办 + 趋势图）
        ├── items.js        商品管理 / 内容审核（通过 / 驳回）
        ├── orders.js       订单管理（详情 / 发货 / 退款处理）
        ├── users.js        用户管理（封禁 / 解封）
        ├── categories.js   类目管理（树 + 新增）
        └── risk.js         风控事件 + 审计日志
```

## 联调真实后端
将 `src/api.js` 的 `USE_MOCK` 改为 `false`，`BASE` 指向后端地址（默认 `http://localhost:8080`）。
后端对应接口见 `idlefish-backend` 的 `AdminController`。

## 权限
演示为 RBAC 简化版（运营/风控/管理员）。真实接入时由后端 `X-Admin-Role` 头与接口鉴权控制。

## 系统管理（F-16 前端闭环）
左侧「系统管理」分组下新增五大模块，与后端 `idlefish-backend` 的 RBAC 系统管理一一对应：

| 菜单 | 前端视图 | 后端接口 |
| --- | --- | --- |
| 管理员 | `views/sysuser.js` | `/api/admin/system/user` |
| 角色管理 | `views/role.js` | `/api/admin/system/role` + `assign-menus` |
| 机构管理 | `views/organization.js` | `/api/admin/system/organization` |
| 菜单管理 | `views/menu.js` | `/api/admin/system/menu` |
| 数据字典 | `views/dict.js` | `/api/admin/system/dict`（types/data/dropdown） |

所有增删改弹窗均使用 `el-form :rules` 规范化表单校验；树形模块（机构/菜单）支持新增子节点与递归删除保护；角色-菜单授权复用 `el-tree` 勾选。

## 启动
```bash
cd pc-admin
./start.sh            # 默认 8787；也可 ./start.sh 9000 指定端口
# 或：python3 -m http.server 8787
# 浏览器打开 http://localhost:8787
```
> 必须以 HTTP 服务方式打开（ES Module `type="module"` 不支持 file:// 协议）。默认 `USE_MOCK=true`，无需后端即可完整体验系统管理全链路；联调真实后端见上文。

