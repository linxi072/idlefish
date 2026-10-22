# PRD｜ITER-T04 搜索与推荐服务化

| 项 | 内容 |
|---|---|
| 编号 | ITER-T04 |
| 维度 / 优先级 | 技术架构演进 / **P1** |
| 状态 | **部分实现（沙箱可运行子集）** |
| 建议负责 | 后端负责人（架构） |
| 关联模块 | ITER-F04（搜推能力深化）、F-14.1 ES 检索、F-14.2 个性化推荐、F-14.3 搜索词运营、ITER-T03（契约门禁）、ITER-T01（可观测性） |
| 编制日期 | 2026-10-02 |

---

## 一、背景与问题

搜索与推荐当前**内嵌在业务单体中**：ES 检索、推荐综合分（行为加权 + 热度衰减 + 冷启动）、搜索词运营（热搜/同义词/屏蔽词）均与订单、商品、营销等逻辑共享同一进程与数据库。

**ITER-F04 落地后将加剧的问题**
1. **迭代耦合**：算法策略变更需随业务版本发布，无法独立灰度与回滚；实验（AB）调整牵一发动全身。
2. **资源争抢**：检索/召回/向量计算属 CPU/内存密集型，与交易主链路（支付、回调）共享资源，峰值时互相影响 —— 尤其支付回调属资金链路，不应被检索负载拖累。
3. **特征无处安置**：实时特征（ITER-F04 REQ-01）需要独立的特征存储与更新通道，单体中难以清晰分层。
4. **可观测性粗粒度**：无法区分「检索慢」与「交易慢」，排障困难（ITER-T01 需要服务级指标）。

**前提条件**：本项应在 ITER-F04（能力深化）之后或与之并行设计；**不建议在能力未定型前拆服务**，否则接口反复变更。

---

## 二、目标与非目标

**目标（可度量）**
- G1：搜索/推荐**独立部署单元**，可独立发布、独立扩缩容，不随业务版本发布。
- G2：检索与召回的负载**不再影响交易主链路**：大促检索峰值下支付回调 P99 无明显劣化。
- G3：建立**特征库**（离线特征 + 实时特征统一存取），供推荐/估价（ITER-F03）复用。
- G4：服务间契约纳入 ITER-T03 门禁，接口变更可校验、可阻断。

**非目标**
- 不做全站微服务化（仅析出搜推域）。
- 不替换 ES 与现有技术栈。
- 不追求多语言技术栈（保持 Java/Spring，降低运维复杂度）。

---

## 三、目标用户与用户故事

| 角色 | 用户故事 |
|---|---|
| 算法 | 作为算法，我希望策略独立发布与灰度，以便快速实验而不影响交易 |
| 后端 | 作为后端，我希望检索负载与交易隔离，以便资金链路稳定 |
| SRE | 作为 SRE，我希望搜推可独立扩缩容，以便按负载特征配置资源 |
| 前端 | 作为前端，我希望搜推接口契约稳定且可校验，以便安心对接 |

---

## 四、需求清单（EARS）

| 编号 | 类型 | 需求描述 |
|---|---|---|
| REQ-01 | Ubiquitous | 搜索与推荐应作为**独立部署单元**运行，拥有独立的发布与扩缩容能力 |
| REQ-02 | Event-driven | 当业务侧商品/用户数据变更时，系统应通过消息（RocketMQ）异步同步至搜推侧索引与特征 |
| REQ-03 | Unwanted | 若搜推服务不可用，业务侧应降级（如返回热门/降级列表或隐藏搜索增强），**不得**导致主流程报错 |
| REQ-04 | Ubiquitous | 搜推服务应**始终**提供健康检查与指标端点，并接入 ITER-T01 的统一可观测性 |
| REQ-05 | Event-driven | 当实时特征更新时，特征库应在 60 秒内可被检索与推荐读取 |
| REQ-06 | Ubiquitous | 服务间接口应定义明确契约，并纳入 ITER-T03 契约一致性校验与 CI 门禁 |
| REQ-07 | Unwanted | 若索引同步滞后超过阈值，系统应告警并在结果中标注数据时效，避免用户看到严重过期内容 |
| REQ-08 | Event-driven | 当检索请求超时或下游异常时，服务应熔断降级并返回可用结果或明确失败，不得雪崩 |
| REQ-09 | Ubiquitous | 搜推服务应**始终**与交易主链路资源隔离（独立线程池/连接池/实例） |
| REQ-10 | Optional | 若需兼容过渡期，应支持「单体内联模式」与「独立服务模式」双模式开关，便于灰度切流 |

---

## 五、流程说明

**服务化后的调用链路**
```
小程序/PC → 业务网关 → [HTTP/gRPC] 搜推服务
  检索：查询词 → 屏蔽词/同义词 → ES 召回 + 语义召回 → 融合排序 → 返回
  推荐：用户 ID → 特征库（实时+离线）→ 召回 → 排序 → AB 分桶 → 返回
```

**数据同步链路**
```
商品/用户/行为变更 → RocketMQ 事件 → 搜推服务消费
  → 更新 ES 索引 / 更新特征库 → 同步滞后监控（REQ-07）
```

**降级链路**：搜推不可用（REQ-03）→ 业务侧返回热门兜底或隐藏增强入口 → 主流程不受影响。

---

## 六、交互说明

- **对前端透明**：接口路径与契约保持稳定，切换由服务端路由/开关控制（REQ-10 双模式）。
- **运维视角**：搜推服务独立实例组，独立看板（检索 QPS/延迟/召回失败率/索引滞后）。
- **契约管理**：新增服务间契约文件纳入 ITER-T03 扫描，CI 校验。
- **灰度切流**：按用户/流量比例从内联模式切至独立服务，可快速回滚。

---

## 七、数据口径与指标

| 指标 | 口径 | 目标 |
|---|---|---|
| 独立发布 | 搜推发布不触发业务发布 | 是 |
| 检索 P99 | 检索接口延迟 P99 | 不劣于拆分前 |
| 交易链路隔离 | 检索峰值下支付回调 P99 劣化幅度 | < 10% |
| 索引同步滞后 | 变更 → 可检索时延 | ≤ 60s（告警阈值可配） |
| 降级可用率 | 降级时返回兜底而非报错的比例 | 100% |
| 契约覆盖 | 服务间接口纳入契约校验比例 | 100% |
| 实时特征时延 | 行为 → 特征可读 | ≤ 60s |

---

## 八、边界场景与异常状态

1. 搜推服务全不可用 → 业务侧降级，搜索/推荐不可用但交易可继续（REQ-03）。
2. 索引同步积压（大批量商品上下架）→ 滞后告警，结果标注时效（REQ-07）。
3. 网络分区导致特征库不可读 → 回落离线特征或热度兜底。
4. 双模式切换期间数据双写/双读不一致 → 需切换窗口与一致性校验。
5. 服务拆分后分布式事务问题 → 搜推为**最终一致**场景，避免强事务依赖。
6. 契约变更未同步导致前端报错 → 由 ITER-T03 门禁阻断（REQ-06）。

---

## 九、权限与合规

- 服务间调用需鉴权（内网 mTLS 或服务令牌），不得裸奔。
- 用户行为特征属个人信息，跨服务传输需加密与最小化（仅传必要特征）。
- 搜推服务日志不得落库敏感个人信息（手机号、身份证）。
- 独立部署后新增网络边界，需纳入安全评审与内网访问控制。

---

## 十、验收标准（可勾选）

- [x] 搜推可独立部署与发布（REQ-01）：`/sr/api/**` 服务面已落地（同一份代码、不同配置的部署单元）；实例级扩缩容复用 F-15.3 容器化资产，由运维侧承接。
- [x] 数据变更通过消息异步同步至索引与特征（REQ-02）：`SearchSyncProducer`（RocketMQ）+ 本地双写 + 消费侧 `applySyncedItem` 补偿回调。
- [x] 搜推不可用时业务侧降级而非报错（REQ-03）：四级降级阶梯（远程 → 内联 → 热门兜底 → 空结果），任一级失败均返回可用结果并标注归因。
- [x] 健康检查与指标接入统一可观测性（REQ-04）：`/sr/api/status` 状态端点 + MetricsRegistry 埋点 + traceId 跨服务透传。
- [x] 实时特征 60 秒内可读（REQ-05）：复用 ITER-F04 `UserRealtimeFeatureService`（Redis 60s 窗口），服务面 `/sr/api/behavior` 回传。
- [x] 服务间契约纳入 ITER-T03 门禁（REQ-06）：`search-recommend.json` 6 端点，auth=service，CI contract-gate 校验。
- [x] 索引滞后超阈值告警并标注时效（REQ-07）：`SyncLagTracker` + `SearchSyncMonitor` 周期评估/冷却告警 + `X-Data-Age-Seconds` 响应头。
- [x] 超时/异常可熔断降级，不雪崩（REQ-08）：`CircuitBreaker` 纯函数状态机（closed→open→half-open）。
- [x] 与交易主链路资源隔离（REQ-09）：专用有界线程池 `SearchServiceExecutor`（守护线程 + AbortPolicy 饱和快速失败）。
- [x] 双模式开关支持灰度切流与回滚（REQ-10）：`idlefish.searchService.mode=inline|remote`，默认 inline 保证离线构建不受影响。

---

## 十一、依赖与风险

| 类型 | 说明 | 应对 |
|---|---|---|
| 依赖 | ITER-F04 能力定型（接口与策略稳定后再拆） | 串行：F04 设计定型 → T04 拆分 |
| 依赖 | ITER-T03 契约门禁（服务间契约校验） | 并行推进，T03 建议提前 |
| 依赖 | RocketMQ（已真实接入）、Redis 特征存储 | 复用现有组件，不引入新中间件 |
| 风险 | 过早拆分导致接口反复变更、成本翻倍 | 严格遵循「先能力后拆分」顺序 |
| 风险 | 分布式链路排障难度上升 | 依赖 ITER-T01 traceId 跨服务透传 |
| 风险 | 运维复杂度上升（多一个部署单元） | 复用 F-15.3 容器化基础，纳入统一运维 |
| 风险 | 离线构建引入新依赖 | 双模式设计，内联模式保证 `mvn -o` 仍可构建 |

---

## 十二、实现说明（2026-10-22 落地）

### 12.1 架构与落地范围

搜推服务化按「端口/适配器 + 双模式」落地，**零新增外部依赖**（离线 `mvn -o` 可编译可测）：

```
业务侧（SearchController / SearchEnhanceController）
   └── SearchServingFacade（唯一入口）
         ├── inline 模式 → InlineSearchRecommendGateway → SearchEnhanceService / RecommendService（进程内）
         └── remote 模式 → HttpSearchRecommendGateway → /sr/api/**（独立部署单元，同一份代码）
               ↑ 熔断器 CircuitBreaker（REQ-08） + 隔离线程池 SearchServiceExecutor（REQ-09）
               ↑ 失败按阶梯降级（REQ-03）：remote → inline → 热门兜底 → 空结果（标注 degraded/reason）
```

- **服务面即本仓库 Controller**（`/sr/api/**`）：契约门禁要求「契约端点必须在代码中真实存在」，故独立部署单元与业务单体为**同一份代码、不同配置**（`mode=remote` 的实例仅暴露服务面），业务侧远程调用即指向该部署单元。
- **执行部门联管线**：服务面 Controller 只调内联适配器，**绝不回连远程**，结构性防环路。
- **服务间鉴权**（REQ-09/权限节）：`X-Service-Token` 头，服务端**未配置即拒绝**（fail-closed），杜绝裸奔。

### 12.2 数据同步与滞后监控（REQ-02/05/07）

- **双写语义**：商品审核通过/编辑/下架/删除 → `SearchSyncService.onItemChanged` ① RocketMQ 投递（`SearchSyncProducer`，HMAC-SHA1 签名，复用 RocketMqDelayServiceImpl 模式）② 本地索引即时更新（过渡期一致性，PRD §八.4）③ 滞后记录。
- **消费侧**：`/sr/api/sync/item` 按 `occurredAt` 回填口径幂等覆盖写，失败上抛供重试。
- **滞后监控**：`SearchSyncMonitor`（30s 周期）远程模式轮询 `/sr/api/status` 回填滞后；超阈值计数 + 5 分钟冷却 `SYSTEM_ALERT` 告警管理员。
- **时效标注**：`ServingOutcome.dataAgeSeconds` + 响应头 `X-Data-Age-Seconds`（REQ-07）。

### 12.3 契约清单（REQ-06）

`src/test/resources/contracts/search-recommend.json`（domain=search-recommend，auth=service，共 6 端点）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sr/api/search/query` | 增强检索（屏蔽词/同义词/融合/AB/零结果兜底一体） |
| GET | `/sr/api/search/guess` | 零结果兜底「猜你想找」 |
| GET | `/sr/api/recommend/feed` | 首页推荐流 |
| POST | `/sr/api/behavior` | 实时行为回传（60s 窗口） |
| POST | `/sr/api/sync/item` | 索引同步应用（消费侧回调/补偿） |
| GET | `/sr/api/status` | 服务状态（模式/熔断/时效/线程池快照） |

> 注：服务控制器以 `@RequestMapping("/sr/api")` 为前缀，上表路径为完整路径。

### 12.4 配置项（`idlefish.searchService`，全部环境变量占位）

| 配置 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关；关闭后所有调用直接走内联（等价历史行为） |
| `mode` | `inline` | inline / remote（REQ-10 灰度切流） |
| `base-url` | 空 | 远程模式基址（未配置 → 远程不可用 → 降级） |
| `service-token` | 空 | 服务间令牌（未配置 → 服务端 fail-closed 拒绝） |
| `timeout-ms` | 2000 | 远程读取超时 / 隔离池任务总超时（毫秒） |
| `connect-timeout-ms` | 500 | 远程连接超时（毫秒） |
| `failure-threshold` | 5 | 连续失败达阈值即熔断（REQ-08） |
| `open-duration-ms` | 10000 | 熔断持续时长，到期半开探测 |
| `core-pool-size` / `max-pool-size` / `queue-capacity` | 8 / 32 / 256 | 隔离线程池（队列饱和 AbortPolicy 快速失败降级，REQ-09） |
| `sync-enabled` | true | 商品变更经 RocketMQ 异步同步（关闭后仅本地双写，REQ-02） |
| `lag-alert-seconds` | 60 | 索引滞后告警阈值（秒，REQ-07） |
| `alert-admin-user-id` | 空 | 滞后告警接收管理员；空则回退 `notify.alertAdminUserId` |

### 12.5 运维端点（`/api/admin/search-service`，管理员鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/status` | 模式/熔断器/滞后/线程池快照 |
| POST | `/resync` | 全量重建索引并重置滞后基线（内联模式；远程模式返回明确提示） |
| POST | `/breaker/reset` | 手动复位熔断器 |

### 12.6 关键文件

- 端口与 DTO：`SearchRecommendGateway` / `dto/{ItemQuery,ItemPage,ServingOutcome,BehaviorReport,ItemSyncRequest,ServingStatusVO}`
- 双实现：`InlineSearchRecommendGateway` / `HttpSearchRecommendGateway`
- 韧性：`CircuitBreaker`（纯函数 `next()` 可单测）/ `SearchServiceExecutor` / `SearchServingException`
- 路由：`SearchServingFacade`（Routed 记录承载 degraded/reason/mode）
- 同步：`SearchSyncProducer` / `SearchSyncService` / `SearchSyncMonitor` / `SyncLagTracker`
- 装配：`SearchServingConfig`（熔断器/线程池/滞后跟踪器 Bean，线程池 `destroyMethod="shutdown"`）
- 接线：`ItemService`（5 处变更点调 `onItemChanged`）、`SearchController`、`SearchEnhanceController`、`IdlefishProperties`、`application.yml`

### 12.7 验证结论（2026-10-22）

- **单元测试**：7 个测试类 45 例全绿（CircuitBreaker 5 / Executor 4 / SyncLag 4 / Inline 3 / Http 11 / Facade 12 / SyncService 6）。
- **契约门禁**：端点 143 → 149（+6），兼容变更 6 项全部放行、0 阻断；基线再生后重跑零漂移。
- **全量回归**：560 例（515 基线 + 45 新增），失败集合与 T03 基线**逐类逐条一致**（8F+14E 均为预存环境问题：种子用户无 openid、微信支付 mock、RSA 私钥文件、mockito 断言），T04 改动零新增失败。
- **修复记录**：全量回归曾暴露 `HttpSearchRecommendGateway` 双构造器无 `@Autowired` 导致 Spring 上下文加载失败的真实缺陷，已修复并以 worktree 基线对照验证零新增失败。
- **遗留**：实例级独立部署（K8s 清单）与真机灰度切流由运维侧承接；沙箱不伪造外部执行。
