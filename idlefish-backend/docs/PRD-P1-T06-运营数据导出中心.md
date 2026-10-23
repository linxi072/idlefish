# PRD-P1-T06 运营数据导出中心（ITER-T06 / REQ-07 闭环）

> 版本：v1.0 ｜ 状态：已实现（本地提交待推送 V1.0.4）｜ 作者：后端团队 ｜ 关联：ITER-T05 多租户/机构数据隔离深化

---

## 一、背景与目标

平台已具备多租户/机构数据隔离能力（ITER-T05：四大数据范围 SELF / SELF_AND_CHILD / APPOINT / ALL、统一 SQL 过滤层、越权审计、机构停用禁登、临时授权、归属迁移）。但在「运营数据导出」这一高频诉求上，仍缺乏一个**受数据权限约束、可审计、防跨机构泄露**的统一导出通道——此前 T05 将导出约束列为遗留项（REQ-07）。

ITER-T06 目标：构建运营数据导出中心，使后台管理员能够按需导出运营数据，**其导出范围严格受 ITER-T05 数据权限裁决约束**，做到：

1. 带 `org_id` 的实体（管理员账号、商品）仅导出「可见机构」数据，越权机构请求被拒绝并记审计；
2. 无 `org_id` 的实体（用户、订单）仅「不受限（ALL）」管理员可导出，杜绝跨机构数据泄露；
3. 每次导出落审计任务（`t_export_task`），敏感字段（手机号）脱敏，CSV 经转义防注入；
4. 下载环节二次范围鉴权，越权下载同样被拒绝并记审计。

---

## 二、需求映射

| 需求编号 | 说明 | 实现位置 |
|---------|------|---------|
| REQ-07-1 | 枚举可导出类型与范围约束提示 | `GET /api/admin/export/types` |
| REQ-07-2 | 导出前预览（校验范围 + 取数不落盘） | `POST /api/admin/export/preview` |
| REQ-07-3 | 执行导出（范围校验 → 取数 → 落盘 → 记任务） | `POST /api/admin/export/export` |
| REQ-07-4 | 下载导出文件（二次范围鉴权） | `GET /api/admin/export/download` |
| REQ-07-5 | 带 `org_id` 实体仅可见机构可导出 | `ExportService.resolveContext` + `DataScopeService` |
| REQ-07-6 | 无 `org_id` 实体仅 ALL 管理员可导出 | `ExportService.resolveContext` |
| REQ-07-7 | 越权导出/下载拒绝并写审计 | `ExportService.writeDenyAudit`（action=`EXPORT_DATA_SCOPE_DENY`） |
| REQ-07-8 | 敏感字段脱敏、CSV 转义、路径穿越防护 | `ExportCsvBuilder` / `LocalExportFileStore` |
| REQ-07-9 | 行数 / 文件体积上限拦截 | `IdlefishProperties.Export` + `ExportService` |

---

## 三、架构设计

### 3.1 分层

```
AdminExportController            # 4 端点（auth=admin），仅做参数接收与响应包装
        │
ExportService                   # 编排：范围裁决 → 取数 → 构造 → 落盘 → 记任务 → 下载鉴权
        │
        ├── DataScopeService     # 复用 ITER-T05：resolveForAdmin / getOrgIds / isUnrestricted
        ├── ExportProvider(×4)   # 策略接口：按类型取数（带/不带 org_id 两类）
        ├── ExportTaskMapper     # t_export_task 持久化
        ├── AuditLogMapper       # 审计（EXPORT_CREATE / EXPORT_DATA_SCOPE_DENY）
        ├── ExportFileStore      # 文件落盘/读取（LocalExportFileStore 防穿越）
        └── IdGenerator          # exportNo() 前缀 EXP
```

### 3.2 导出类型与范围语义

`ExportType` 枚举区分两类实体：

| 类型 | code | hasOrgScope | 导出约束 |
|------|------|-------------|---------|
| 管理员账号 | `admin_user` | true | 仅可见机构（`org_id IN (可见集合)`） |
| 商品 | `item` | true | 仅可见机构（同上） |
| 用户 | `user` | false | 仅 ALL（不受限）管理员可导出 |
| 订单 | `order` | false | 仅 ALL（不受限）管理员可导出 |

`hasOrgScope=true` 类型支持 `ExportQuery.orgIds` 做「可见机构子集」覆盖；`hasOrgScope=false` 类型忽略 orgIds，仅 ALL 可导出。

### 3.3 安全模型（核心）

```
resolveContext(adminId, provider, query):
    decision = dataScopeService.resolveForAdmin(adminId)   # ITER-T05 决议
    if !provider.hasOrgScope():
        if !decision.isUnrestricted():                     # 非 ALL 管理员
            writeDenyAudit(EXPORT_DATA_SCOPE_DENY)
            throw EXPORT_DATA_SCOPE_FORBIDDEN
        return ExportContext(limit, null)                  # 全量
    allowed = decision.getOrgIds()                         # null = ALL
    if query.orgIds 非空:
        bad = query.orgIds - allowed                       # 仅 ALL 时 allowed=null 表示全放行
        if bad 非空:
            writeDenyAudit(EXPORT_DATA_SCOPE_DENY, bad)
            throw EXPORT_DATA_SCOPE_FORBIDDEN
        return ExportContext(limit, query.orgIds)
    return ExportContext(limit, allowed)

authorizeDownload(adminId, task):                         # 二次鉴权
    decision = dataScopeService.resolveForAdmin(adminId)
    taskOrgIds = parseSnapshot(task.orgIdsSnapshot)        # null = 全量任务
    if taskOrgIds == null:
        if !decision.isUnrestricted(): 拒绝
    elif allowed != null && !allowed.containsAll(taskOrgIds): 拒绝
```

越权一律写 `t_audit_log`（action=`EXPORT_DATA_SCOPE_DENY`，target_type=`export`），不影响主流程（审计写失败被吞掉）。

---

## 四、接口契约

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/admin/export/types` | admin | 枚举可导出类型（code/label/hasOrgScope） |
| POST | `/api/admin/export/preview` | admin | 预览：headers / rowCount / orgIds（null=全量），不落盘 |
| POST | `/api/admin/export/export` | admin | 执行导出，返回 `ExportTask`（exportNo/type/rowCount/fileName/status） |
| GET | `/api/admin/export/download` | admin | 下载 CSV 附件（二次范围鉴权） |

契约文件：`src/test/resources/contracts/export.json`；基线端点计数 156 → **160**（含本迭代 4 端点）。
门禁：`ContractConsistencyTest`（漂移断言）+ `ContractCompatibilityTest`（不兼容阻断），离线全绿。

### 入参 `ExportQuery`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 是 | 导出类型 code；为空/非法抛 `EXPORT_TYPE_UNSUPPORTED` |
| orgIds | List<Long> | 否 | 仅 `hasOrgScope=true` 有效，须为可见机构子集 |
| limit | Integer | 否 | 行数上限；缺省取 `maxRowsPerExport`，超配抛 `EXPORT_LIMIT_EXCEEDED` |
| format | String | 否 | 默认 CSV；仅支持 CSV（否则 `EXPORT_TYPE_UNSUPPORTED`） |

---

## 五、数据模型

### 5.1 `t_export_task`（新增，幂等 DDL）

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 雪花主键（BaseEntity） |
| export_no | varchar | 导出单号（前缀 EXP，唯一 `uk_export_no`） |
| type | varchar | 导出类型 code |
| admin_id | bigint | 操作管理员 |
| org_ids_snapshot | varchar | 机构快照（JSON 数组，null/[] 表示全量） |
| row_count | int | 导出行数 |
| format | varchar | 文件格式（CSV） |
| file_key | varchar | 落盘文件键（防穿越校验） |
| file_name | varchar | 下载文件名 |
| status | varchar | 任务状态（DONE 等） |
| create_time / update_time | datetime | 自动填充 |

DDL 采用 `CREATE TABLE IF NOT EXISTS`，随主库 schema 幂等迁移。

### 5.2 文件落盘

`LocalExportFileStore` 以 `idlefish.export.storage-dir`（默认 `export-files`）为根，`resolve` 拦截含父路径/穿越字符的文件键（如 `../`、绝对路径），仅允许扁平文件名，杜绝路径穿越。

---

## 六、配置

`IdlefishProperties.Export` 内部类（默认值即可运行，无需改 `application.yml`）：

| 配置项 | 默认 | 环境变量覆盖 | 说明 |
|--------|------|-------------|------|
| maxRowsPerExport | 50000 | `IDLEFISH_EXPORT_MAX_ROWS` | 单次导出行数上限 |
| maxFileMb | 50 | `IDLEFISH_EXPORT_MAX_FILE_MB` | 导出文件体积上限 |
| storageDir | `export-files` | `IDLEFISH_EXPORT_STORAGE_DIR` | 落盘目录 |

---

## 七、测试与门禁

| 测试 | 类型 | 覆盖 |
|------|------|------|
| `ExportCsvBuilderTest` | 纯函数 | 转义 / 脱敏 / 行连接 |
| `ExportQueryTest` / `ExportTypeTest` | 枚举/解析 | 非法 code 抛 `EXPORT_TYPE_UNSUPPORTED` |
| `ExportServiceTest` | Mockito | 范围裁决、越权审计、快照序列化、下载鉴权 |
| `AdminExportControllerTest` | Mockito | 4 端点编排 |
| `ContractConsistencyTest` / `ContractCompatibilityTest` | 离线门禁 | 160 端点全绿 |

所有单测纯 Mockito，离线可跑；`@SpringBootTest` 需真实 MySQL（@Transactional 回滚），不在离线门禁范围。

---

## 八、错误码

| Code | 值 | 含义 |
|------|----|------|
| EXPORT_TYPE_UNSUPPORTED | 30041 | 导出类型不支持 / 格式不支持 |
| EXPORT_LIMIT_EXCEEDED | 30042 | 行数或文件体积超限 |
| EXPORT_DATA_SCOPE_FORBIDDEN | 30043 | 越权导出/下载被拒绝 |
| EXPORT_TASK_NOT_FOUND | 30044 | 导出任务不存在 |
| EXPORT_FILE_NOT_FOUND | 30045 | 导出文件不存在 |

---

## 九、风险与遗留

- **遗留**：导出任务暂无异步化（当前为同步取数+落盘，单请求内完成）；大数据量导出建议后续演进为异步任务 + 进度查询（可作 ITER-T07 候选）。
- **遗留**：导出格式仅 CSV；后续如需 Excel 可扩展 `ExportProvider` 输出与 `format` 枚举。
- **遗留**：文件存储当前为本地磁盘；多副本/对象存储可替换 `ExportFileStore` 实现，接口已抽象。
- **安全**：手机号脱敏依赖 `CryptoUtil.maskPhone`；用户导出需解密后再脱敏，确保明文不在落盘文件出现。
