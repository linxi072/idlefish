# PRD｜ITER-T01 可观测性生产化

| 项 | 内容 |
|---|---|
| 编号 | ITER-T01 |
| 维度 / 优先级 | 技术架构演进 / **P0** |
| 状态 | 待评审 |
| 建议负责 | 后端负责人 + SRE（看板与告警） |
| 关联模块 | F-12.6（`MetricsRegistry` + `MicrometerMetricsBridge` + `-Pmicrometer` profile）、`ObservabilityFilter`（MDC traceId）、`ObservabilityController`（health/metrics + MySQL/Redis/ES/RocketMQ/OSS 探活） |
| 编制日期 | 2026-10-02 |

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

- [ ] 默认构建（不加 `-Pmicrometer`）启动后 `/actuator/prometheus` 可访问（REQ-01）。
- [ ] 任一请求日志均含 traceId，覆盖率 100%（REQ-02）。
- [ ] 异步任务/消息消费中 traceId 与父请求一致（REQ-03）。
- [ ] 支付回调、分账、提现动作均产生可查询 span（REQ-04）。
- [ ] 指标采集失败时主流程不受影响，仅降级（REQ-05）。
- [ ] 构造重复流水场景，触发 P0 资损告警（REQ-07）。
- [ ] 核心接口错误率/P99 超阈值触发告警并触达值班渠道（REQ-08）。
- [ ] 自研 `MetricsRegistry` 与 Micrometer 指标口径一致，无重复/冲突指标（REQ-09）。
- [ ] 4 张 Grafana 看板上线，告警分级规则生效。
- [ ] `/actuator/**` 已限制内网访问，敏感端点鉴权通过。

---

## 十一、依赖与风险

| 类型 | 说明 | 应对 |
|---|---|---|
| 依赖 | Prometheus + Grafana + Alertmanager 部署；OTel SDK/Collector | 复用现有容器化基础（F-15.3），先单环境试点 |
| 依赖 | 离线构建约束：`.m2` 需缓存 micrometer/OTel 依赖，否则破坏 `mvn -o` | **先验证离线编译**，再合入默认构建；失败则保留 profile 但改默认启用 |
| 风险 | 将 micrometer 移出可选 profile 可能破坏沙箱离线构建 | 分两步：先保证依赖入 `.m2`，再切换默认；保留回滚开关 |
| 风险 | 打点不当引发性能损耗 | 异步上报 + 低基数标签 + 采样；压测验证损耗 < 3% |
| 风险 | 告警噪音导致值班疲劳 | 分组/抑制/分级，初期人工调优阈值 |
