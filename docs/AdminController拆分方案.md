# AdminController 拆分方案（按业务领域提取单一职责控制器）

> 对象：`idlefish-backend/src/main/java/com/idlefish/trade/admin/controller/AdminController.java`（24 个方法，统一挂在类级 `@RequestMapping("/api/admin")` 下）
> 目标：按业务功能领域归类，提取为多个职责单一的控制器类；**保持原有 API 路由、请求映射、方法签名与业务逻辑不变**。

## 一、拆分原则

1. **路由零变化**：每个新控制器保留类级 `@RequestMapping("/api/admin")`，方法级 `@GetMapping`/`@PostMapping` 路径与 HTTP 方法原样迁移，对外接口完全不变。
2. **签名零变化**：方法参数（`@CurrentAdmin AdminUser`、`@RequestParam`、`@PathVariable`）、返回类型 `Result<...>`、方法名均原样保留。
3. **逻辑零变化**：方法体逐字迁移，不重写业务；仅做"搬移"，不改行为。
4. **单一职责**：每个控制器只注入其真正依赖的服务，按领域收敛。
5. **无外部耦合**：经检索 `AdminController` 仅被自身与路线图文档引用，拆分后可安全删除原类，无调用方受影响。

## 二、新控制器清单（8 个）

| 新控制器类 | 负责功能领域 | 路由前缀 | 注入依赖（仅所需） |
|---|---|---|---|
| `AdminAuthController` | 认证登录：后台账号登录签发管理员 JWT | `/api/admin/auth` | `AdminUserMapper`、`JwtUtil` |
| `AdminItemAuditController` | 商品内容审核：审核列表 / 通过 / 驳回 | `/api/admin/items` | `AdminService` |
| `AdminOrderController` | 订单管理：列表 / 详情 / 代发货 / 代退款 | `/api/admin/orders` | `AdminService`、`OrderService`、`RefundService` |
| `AdminUserController` | 用户管理：用户列表 / 封禁解封 | `/api/admin/users` | `AdminService` |
| `AdminCategoryController` | 类目与属性模板：类目增/树、属性模板增/列 | `/api/admin/categories`、`/api/admin/attr-templates`、`/api/admin/attr-template` | `AdminService`、`CategoryService`、`AttrTemplateService` |
| `AdminRiskAuditController` | 风控与审计：风控事件列表 / 审计日志列表 | `/api/admin/risks`、`/api/admin/audit-logs` | `AdminService` |
| `AdminFinanceController` | 资金与财务：提现审批 / 资金对账 / 结算解冻 | `/api/admin/withdrawals`、`/api/admin/reconciliation`、`/api/admin/settlements` | `WithdrawalService`、`ReconciliationService`、`SettlementService` |
| `AdminReviewController` | 评价管理：待审列表 / 通过 / 驳回 | `/api/admin/reviews` | `ReviewService` |

> 说明：原 `AdminController` 中并无独立的「权限管理（RBAC 角色 CRUD）」「系统配置」方法，登录态鉴权仅 `login` 一项；故权限相关归入 `AdminAuthController`（认证），系统配置当前无对应方法，预留不建空类。

## 三、原方法 → 新控制器 归属划分清单（24 项）

| # | 原方法名 | HTTP | 原路由 | 归属新控制器 |
|---|---|---|---|---|
| 1 | `login` | POST | `/api/admin/auth/login` | **AdminAuthController** |
| 2 | `items`（审核列表） | GET | `/api/admin/items` | **AdminItemAuditController** |
| 3 | `approve` | POST | `/api/admin/items/{itemId}/approve` | **AdminItemAuditController** |
| 4 | `reject` | POST | `/api/admin/items/{itemId}/reject` | **AdminItemAuditController** |
| 5 | `orders` | GET | `/api/admin/orders` | **AdminOrderController** |
| 6 | `orderDetail` | GET | `/api/admin/orders/{orderNo}` | **AdminOrderController** |
| 7 | `ship` | POST | `/api/admin/orders/{orderNo}/ship` | **AdminOrderController** |
| 8 | `refund` | POST | `/api/admin/orders/{orderNo}/refund` | **AdminOrderController** |
| 9 | `users` | GET | `/api/admin/users` | **AdminUserController** |
| 10 | `ban` | POST | `/api/admin/users/{userId}/ban` | **AdminUserController** |
| 11 | `categories`（新增类目） | POST | `/api/admin/categories` | **AdminCategoryController** |
| 12 | `categoryTree` | GET | `/api/admin/categories` | **AdminCategoryController** |
| 13 | `attrTemplates` | GET | `/api/admin/attr-templates` | **AdminCategoryController** |
| 14 | `attrTemplate` | POST | `/api/admin/attr-template` | **AdminCategoryController** |
| 15 | `risks` | GET | `/api/admin/risks` | **AdminRiskAuditController** |
| 16 | `auditLogs` | GET | `/api/admin/audit-logs` | **AdminRiskAuditController** |
| 17 | `withdrawals` | GET | `/api/admin/withdrawals` | **AdminFinanceController** |
| 18 | `approveWithdrawal` | POST | `/api/admin/withdrawals/{id}/approve` | **AdminFinanceController** |
| 19 | `rejectWithdrawal` | POST | `/api/admin/withdrawals/{id}/reject` | **AdminFinanceController** |
| 20 | `reconciliation` | GET | `/api/admin/reconciliation` | **AdminFinanceController** |
| 21 | `unfreezeSettlement` | POST | `/api/admin/settlements/{id}/unfreeze` | **AdminFinanceController** |
| 22 | `reviews` | GET | `/api/admin/reviews` | **AdminReviewController** |
| 23 | `approveReview` | POST | `/api/admin/reviews/{id}/approve` | **AdminReviewController** |
| 24 | `rejectReview` | POST | `/api/admin/reviews/{id}/reject` | **AdminReviewController** |

## 四、实施要点

- **类级映射保持一致**：每个新控制器均保留 `@RestController` + `@RequestMapping("/api/admin")`，确保完整路径与现状一致；同一路径不同 HTTP 方法（如 `categories` 的 GET 树 / POST 新增）在单控制器内共存，无冲突。
- **依赖收敛**：`adminService` 被 `AdminItemAuditController`、`AdminOrderController`、`AdminUserController`、`AdminCategoryController`、`AdminRiskAuditController` 共享（Spring 单例复用）；其余服务按需注入，原 11 参数巨型构造器被拆散。
- **原类处置**：全部 24 个方法迁出后，**删除 `AdminController.java`**（无任何外部引用），避免重复 `@RequestMapping("/api/admin")` 造成歧义。
- **验证**：离线 `mvn -o test-compile` 全绿；路由可通过启动后 `curl /api/admin/...` 逐个比对，业务行为不变。

## 五、风险与回滚

- **风险**：低。纯搬移，无逻辑改动；路由前缀统一，无路径重叠。
- **回滚**：保留原 `AdminController` 即可一键还原（git 回退该删除/新增提交）；无数据库/配置变更。
