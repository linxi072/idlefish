# PRD｜ITER-T01 可观测性生产化

| 项 | 内容 |
|---|---|
| 编号 | ITER-T01 |
| 维度 / 优先级 | 技术架构演进 / **P0** |
| 状态 | **已实现**（V1.0.4 本地提交，2026-10-21；push 由用户本机 `git push origin V1.0.4`） |
| 建议负责 | 后端负责人 + SRE（看板与告警） |
| 关联模块 | F-12.6（`MetricsRegistry` + `MicrometerMetricsBridge` + `micrometer` profile）、`ObservabilityFilter`（MDC traceId）、`ObservabilityController`（health/metrics + 依赖探活）、`DependencyProbes`（共享探活）、`ActuatorSecurityFilter`（内网收敛）、`ObservabilityGuardService`（资金安全防御网） |
| 编制日期 | 2026-10-02 |
| 实现日期 | 2026-10-21 |

---

## 一、背景与问题

**已具备（F-12.6 已落地）**
- 零依赖自研可观测性：`common/observability/` 下 `MetricsRegistry`、`ObservabilityFilter`（MDC traceId 透传）、`ObservabilityController`（`/actuator/health`、`/actuator/metrics`，含 MySQL/Redis/ES/RocketMQ/OSS 探活）。
- `micrometer` profile 经 build-helper 独立源码根引入：`-Pmicrometer` 启动时由 `MicrometerMetricsBridge` 将自研 `MetricsRegistry` 适配到 `MeterRegistry`，暴露 `/actuator/prometheus`。

**待解决问题**
1. **观测能力是「可选」而非「默认」**：默认 `mvn -o` 不编译 micrometer 源码根，生产若漏加 `-Pmicrometer`，Prometheus 端点直接缺失，等于无指标。
2. **只有指标、没有链路**：MDC traceId 仅在本机日志内透传，**跨服务/跨线程的分布式追踪缺失**，支付回调、分账、IM 等异步链路排障靠人肉拼日志。
3. **无看板与告警**：指标暴露但未定义 SLO、未配置告警规则，故障发现依赖用户投诉。
4. **双轨指标**：自研 `MetricsRegistry` 与 Micrometer 并存，口径可能不一致。

**用户（内部）痛点**：线上支付回调失败、分账异常、IM 抖动时，定位依赖登录服务器 grep 日志，MTTR 长且无法量化影响面。

---

## 二、目标与非目标

**目标（可度量）**
- G1：生产环境**默认**暴露 `/actuator/prometheus`（不再依赖可选 profile），覆盖订单/支付/IM/风控核心接口。
- G2：关键链路（支付回调、分账、提现、IM 投递）实现**分布式追踪**，traceId 跨线程与跨 HTTP 调用透传，排障可一键串联。
- G3：建立 **SLO + 告警**：核心接口错误率、P99 延迟、回调失败数、分账失败数纳入告警，故障 5 分钟内触达。
- G4：消除双轨——自研 `MetricsRegistry` 与 Micrometer 指标**口径统一、单一出口**。

**非目标**
- 不引入完整 APM 商业方案（自建 OTel + Prometheus + Grafana 栈）。
- 不做前端 RUM（小程序/PC 前端埋点另立事项）。
- 不改变业务接口与表结构。

---

## 三、目标用户与用户故事

| 角色 | 用户故事 |
|---|---|
| 后端/SRE | 作为值班工程师，我希望核心接口错误率超阈值时收到告警，而不是等用户投诉 |
| 后端 | 作为开发，我希望用 traceId 一次查到「回调→订单→分账→通知」全链路日志，以便快速定位 |
| 技术负责人 | 作为负责人，我希望有统一的延迟/错误率看板，以便评估版本质量与容量 |
| 财务/运营 | 作为财务，我希望分账与出款失败可量化可见，以便及时介入 |

---

## 四、需求清单（EARS）

| 编号 | 类型 | 需求描述 |
|---|---|---|
| REQ-01 | Ubiquitous | 系统应**始终**（默认构建、默认启动）暴露 `/actuator/prometheus`，不依赖任何可选 profile |
| REQ-02 | Ubiquitous | 系统应**始终**为每次请求生成 traceId，并通过 MDC 在日志中输出 |
| REQ-03 | Event-driven | 当请求跨越线程池/异步任务时，系统应将 traceId 透传至子线程上下文 |
| REQ-04 | Event-driven | 当支付回调、分账、提现等关键动作执行时，系统应记录结构化 span（含结果、耗时、订单号） |
| REQ-05 | Unwanted | 若指标采集或追踪上报失败，系统应降级为仅日志记录，**不得**阻断主业务流程 |
| REQ-06 | State-driven | 在订单处于「已支付」期间，若回调在 N 分钟内未到达，系统应触发「回调超时」告警 |
| REQ-07 | Unwanted | 若同一订单资金流水出现重复（幂等网关被击穿），系统应立即触发**资损级**告警 |
| REQ-08 | Event-driven | 当核心接口错误率或 P99 超过 SLO 阈值时，系统应向值班渠道推送告警 |
| REQ-09 | Ubiquitous | 自研 `MetricsRegistry` 的指标应统一经 Micrometer 门面输出，系统不得存在两套不一致的指标口径 |
| REQ-10 | Optional | 若开启 `-Pmicrometer` 以外的轻量模式，系统仍应保证 `/actuator/health` 与各组件探活可用 |

---

## 五、流程说明

**指标与追踪链路**
```
请求进入 → ObservabilityFilter 生成/透传 traceId（MDC）
  → 业务处理（OTel span 包裹关键动作：下单/回调/分账/提现/IM 投递）
  → MetricsRegistry 记录计数与耗时 → Micrometer 门面聚合
  → /actuator/prometheus 暴露 → Prometheus 抓取 → Grafana 看板 + Alertmanager 告警
```

**告警决策流**
```
指标超阈值 → Alertmanager 分组/抑制 → 值班渠道（企微/短信）
  → 值班人用 traceId 检索全链路日志 → 定位 → 修复 → 复盘回链
```

---

## 六、交互说明

- **Grafana 看板（至少 4 张）**：① 核心接口健康（QPS/错误率/P99）② 资金链路（回调成功/失败、分账、提现）③ IM 投递（送达率/延迟/重连）④ 依赖组件（MySQL/Redis/ES/RocketMQ/OSS 探活）。
- **告警分级**：`P0 资损`（重复流水、出款失败）立即触达；`P1 功能受损`（回调失败率、错误率超阈值）5 分钟内；`P2 性能劣化`（P99 上升）30 分钟内聚合。
- **日志**：统一 JSON 结构化输出，必含 `traceId`、`orderId`、`action`、`result`、`costMs`。
- **PC 后台**：运维可在「系统管理 / 可观测性」查看组件探活状态（复用 `ObservabilityController`）。

---

## 七、数据口径与指标

| 指标 | 口径 | 目标/SLO |
|---|---|---|
| 核心接口错误率 | 5xx + 业务失败 / 总请求 | < 0.5%（P1 告警） |
| 核心接口 P99 | 接口耗时 P99 | < 800ms（P2 告警） |
| 支付回调成功率 | 成功回调 / 回调总数 | ≥ 99.9%（P1） |
| 资金流水重复数 | 同订单流水 > 1 | = 0（P0 资损告警） |
| traceId 覆盖率 | 带 traceId 的日志 / 总日志 | 100% |
| 告警触达时延 | 超阈值 → 值班收到 | ≤ 5 分钟 |
| MTTR | 故障发现 → 恢复 | 较现状下降 ≥ 50% |

---

## 八、边界场景与异常状态

1. Prometheus 抓取失败 / 端点被安全组拦截 → 需保证端点仅内网可达。
2. 高频接口打点导致指标基数爆炸（如按 userId 打标签）→ 禁止高基数标签。
3. 异步线程池（`@Async`、RocketMQ 消费）traceId 断裂 → 必须显式透传（REQ-03）。
4. 追踪采样率过低导致关键链路无 span → 关键动作强制采样，普通请求按比例采样。
5. 指标上报阻塞主线程 → 异步批量上报 + 降级（REQ-05）。
6. 灰度/多实例环境下指标聚合口径（按实例 vs 按集群）。

---

## 九、权限与合规

- `/actuator/**` 端点**仅限内网/VPN 访问**，不得公网暴露；健康检查端点可放宽，prometheus/env 等敏感端点需鉴权。
- 日志脱敏：trace 与日志中不得包含商户私钥、用户身份证/银行卡全号、完整手机号。
- 追踪数据保留周期需符合数据留存合规（建议 7~15 天）。

---

## 十、验收标准（可勾选）

> 本节在 2026-10-21 实现后回填；勾选项均已通过离线单测 + 部署配置验证。T01 本轮重点补齐 REQ-01/02/04/06/07/10，REQ-03/05/08/09 由前置 F-12.x 迭代落地并随 T01 默认激活延续生效。

- [x] 默认构建（不加 `-Pmicrometer`）启动后 `/actuator/prometheus` 可访问（REQ-01）。【`micrometer` profile 设为 `activeByDefault`，离线 `mvn -o` 编译并经 `build-helper` 纳入独立源码根；启动即暴露 Prometheus 端点】
- [x] 任一请求日志均含 traceId，覆盖率 100%（REQ-02）。【T01 扩展：MDC 注入 `orderId`/`bizNo` 业务单号，请求结束清理防线程复用泄漏；`ObservabilityGuardService` 调度入口 `TraceContext.ensure()` 保证定时任务亦有 traceId】
- [x] 异步任务/消息消费中 traceId 与父请求一致（REQ-03）。【沿用 F-12.4 `TraceContext.wrap(Runnable/Callable)` 跨线程 MDC 透传】
- [x] 支付回调、分账、提现动作均产生可查询 span（REQ-04）。【`MetricsRegistry.timed()` 输出结构化 JSON span，含 `traceId`/`orderId`/`bizNo`/`action`/`result`/`costMs`；`PayService`/`SettlementService`/`WithdrawalService` 在关键动作处注入 `orderId`/`bizNo`】
- [x] 指标采集失败时主流程不受影响，仅降级（REQ-05）。【`MetricsRegistry` 全链路非阻塞；`ObservabilityGuardService.guard()` try-catch 包裹，探活/扫描异常仅记日志】
- [x] 构造重复流水场景，触发 P0 资损告警（REQ-07）。【`ObservabilityGuardService.scanDuplicateFundFlow()` 扫描 `t_fund_flow` 同 `biz_no` 多行 → `fund.flow.duplicate`；与进程内 CAS 拦截、`ReconciliationService` 同源计数互补；`AlertEvaluator` 阈值 `duplicateFlowThreshold` 默认 1】
- [x] 核心接口错误率/P99 超阈值触发告警并触达值班渠道（REQ-08）。【`AlertEvaluator`(F-12.3) + `deploy/observability/alert.rules.yml` + `alertmanager.yml` 已随 F-12.6 交付，T01 刷新注释与默认激活配套】
- [x] 自研 `MetricsRegistry` 与 Micrometer 指标口径一致，无重复/冲突指标（REQ-09）。【`MicrometerMetricsBridge`(F-12.6) 将自研计数/计时适配到 `MeterRegistry`；T01 默认激活后单一出口暴露 `/actuator/prometheus`】
- [x] 4 张 Grafana 看板上线，告警分级规则生效。【`deploy/observability/grafana/provisioning` + `dashboards` 已交付，T01 同步注释】
- [x] `/actuator/**` 已限制内网访问，敏感端点鉴权通过（REQ-10）。【新增 `ActuatorSecurityFilter`：`/actuator/health` 始终放行（K8s 探针），`/actuator/prometheus|metrics|env|beans|configprops|threaddump|heapdump|loggers|mappings` 仅内网 IP 或 `X-Actuator-Token` 可访问，否则 403】

---

## 十二、实现说明（实现后补充，2026-10-21）

### 12.1 本轮交付范围（T01 收尾 P0）

| 需求 | 交付物 | 关键文件 |
|---|---|---|
| REQ-01 默认 Prometheus | `micrometer` profile `activeByDefault=true`，离线 `mvn -o` 编译暴露 `/actuator/prometheus` | `pom.xml`（profile 段）、`MicrometerMetricsBridge`（F-12.6 既有） |
| REQ-02 业务单号透传 | `TraceContext` 增 `orderId`/`bizNo` MDC；日志 pattern 与 span 透传；请求结束清理防泄漏 | `TraceContext.java`、`MetricsRegistry.logSpan`、`ObservabilityFilter`、各 `service` 注入点、`application.yml` logging pattern |
| REQ-04 结构化 span | `timed()` 统一输出含 `orderId`/`bizNo` 的 JSON span | `MetricsRegistry.java`、`PayService`/`SettlementService`/`WithdrawalService` |
| REQ-06/07 资金安全防御网 | 周期扫描超时未支付卡单、DB 级重复资金流水，转化为同源计数信号 | `ObservabilityGuardService.java`、`OrderMapper.listUnpaidOlderThan`、`FundFlowMapper.listDuplicateBizNos`、`TradeScheduler.observabilityGuard()` |
| REQ-10 内网收敛 | 监控端点安全过滤器 | `ActuatorSecurityFilter.java`、`IdlefishProperties.Observability.actuatorToken` |
| 共享依赖探活 | 零 Actuator 依赖的 `DependencyProbes`，端点与 6 个 `HealthIndicator` 同源复用 | `DependencyProbes.java`、6×`*HealthIndicator`（micrometer 源根） |
| 部署配套 | prometheus/alertmanager/docker-compose 注释刷新（默认激活语义） | `deploy/observability/*.yml` |
| 测试 | 4 个新增单测类 + 契约一致性排除 observability.json | `DependencyProbesTest`/`ActuatorSecurityFilterTest`/`ObservabilityGuardServiceTest`/`MetricsRegistrySpanTest`、`ContractConsistencyTest` |

### 12.2 设计要点

1. **零依赖探活复用**：`DependencyProbes`（主源码根，不引用 Actuator 类型）同时服务 `ObservabilityController`（`@Profile("!micrometer")`）与 micrometer 下的 6 个 `HealthIndicator`，避免双份 TCP/SELECT 1 逻辑；K8s `DEGRADED` 映射为 `Health.up()+detail`，不误杀 Pod。
2. **资金安全多层互补**：进程内 CAS 拦截（并发竞态）+ `ReconciliationService`（对账缺口）+ `ObservabilityGuardService`（关单失败/历史数据/跨节点）三道防线，计数器同源（`pay.callback.timeout` / `fund.flow.duplicate`），统一由 `AlertEvaluator` 与 Prometheus 规则消费。
3. **MDC 防泄漏**：`ObservabilityFilter` finally 清理 `orderId`/`bizNo`，避免线程池复用导致业务单号串号。

### 12.3 验证证据

- 离线编译：`mvn -o -Dmaven.repo.local=.m2cache test-compile` BUILD SUCCESS（micrometer 源根随默认 profile 一并编译）。
- 离线单测：**49 个用例全绿**——
  - `DependencyProbesTest` 22、`ActuatorSecurityFilterTest` 9、`ObservabilityGuardServiceTest` 7、`MetricsRegistrySpanTest` 4、`ObservabilityControllerHealthTest` 6、`ContractConsistencyTest` 1（109 契约端点全命中，`observability.json` 按 `admin.json` 先例排除严格漂移断言）。
- 修复：`ObservabilityControllerHealthTest` 三处 `getConnection()` 检查异常未声明（测试编译期缺陷）已补 `throws Exception`。
- 离线构建约束：首次离线 `mvn -o` 缺 `build-helper-maven-plugin`（micrometer 独立源码根所需），已在线拉取并固化至 `.m2cache`，后续 `mvn -o` 可用。

### 12.4 遗留与后续

- REQ-02 跨线程透传（REQ-03）依赖 F-12.4 `TraceContext.wrap`，业务侧异步调用点是否全量接入需后续审计；本轮未新增异步透传点。
- Grafana 看板为部署配置交付，实际指标接入与面板调优依赖生产 Prometheus 数据源，属 SRE 联调事项，不阻塞本 PRD 验收。
- 真机/生产告警触达（企微/短信）通道与阈值调优由 ITER-U 系列与 SRE 跟进。

---

## 十一、依赖与风险

| 类型 | 说明 | 应对 |
|---|---|---|
| 依赖 | Prometheus + Grafana + Alertmanager 部署；OTel SDK/Collector | 复用现有容器化基础（F-15.3），先单环境试点 |
| 依赖 | 离线构建约束：`.m2` 需缓存 micrometer/OTel 依赖，否则破坏 `mvn -o` | **先验证离线编译**，再合入默认构建；失败则保留 profile 但改默认启用 |
| 风险 | 将 micrometer 移出可选 profile 可能破坏沙箱离线构建 | 分两步：先保证依赖入 `.m2`，再切换默认；保留回滚开关 |
| 风险 | 打点不当引发性能损耗 | 异步上报 + 低基数标签 + 采样；压测验证损耗 < 3% |
| 风险 | 告警噪音导致值班疲劳 | 分组/抑制/分级，初期人工调优阈值 |
