# 可观测性生产化工具链（T01）

将「零依赖可观测性内核」（F-12.x）升级为可落地的生产化监控套件：结构化 span 日志、
有界蓄水池分位数（P99 SLO）、六类阈值告警，并配套 Prometheus / Alertmanager / Grafana 一键编排。

## 组件与职责

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| MetricsRegistry | `src/main/.../observability/MetricsRegistry.java` | 零依赖指标注册表；Timer 新增 P50/P95/P99 蓄水池分位数；`timed/timedRun` 输出结构化 JSON span 日志（REQ-04） |
| AlertEvaluator | `src/main/.../observability/AlertEvaluator.java` | 周期性（60s）评估六类异常并冷却去重推送管理员告警 |
| IdlefishProperties.Observability | `src/main/.../IdlefishProperties.java` | 阈值配置：`p99ThresholdMs` / `duplicateFlowThreshold` / `callbackTimeoutThreshold` |
| ReconciliationService | `src/main/.../trade/service/ReconciliationService.java` | 对账不平且有已支付订单时累加 `pay.callback.timeout`（REQ-06 埋点） |
| Refund/Withdrawal/PayService | `src/main/.../trade/service/*` | `timed/timedRun` 包裹出款/回调；CAS 跳过分支累加 `fund.flow.duplicate`（REQ-07 埋点） |
| MicrometerMetricsBridge | `src/main/micrometer/.../MicrometerMetricsBridge.java` | 桥接零依赖指标到 Micrometer，经 `/actuator/prometheus` 暴露（仅 `-Pmicrometer`） |
| Prometheus | `deploy/observability/prometheus.yml` + `alert.rules.yml` | 抓取指标、评估告警规则（6 条） |
| Alertmanager | `deploy/observability/alertmanager.yml` | 告警路由与去重（webhook 占位，请替换为实际网关） |
| Grafana | `deploy/observability/grafana/**` | 数据源 + 4 张看板自动 provisioning |

## 指标契约（桥接后 Prometheus 命名）

- 计数器：`idlefish_counter{name="..."}` —— 业务次数（如 `pay.notify.v3.success`、`fund.flow.duplicate`、`pay.callback.timeout`）
- 计时器：`idlefish_timer_count{name="..."}` / `idlefish_timer_sum_ms` / `idlefish_timer_max_ms` / `idlefish_timer_p50_ms` / `idlefish_timer_p95_ms` / `idlefish_timer_p99_ms`
  - 计时器名以 `.latency` 结尾（如 `pay.callback.apply.latency`、`http.latency./api/x`），P99 SLO 据此评估（REQ-08）

## 六类告警

1. 接口错误率超 5%（`http.status.5xx` / `http.request.*`）
2. 支付验签失败激增（`pay.notify.v3.failure` 近 5m > 5）
3. 慢请求（按路由均值 > 1000ms）
4. **P99 延迟 SLO（REQ-08）**：任意 `*.latency` 计时器 P99 > 2000ms
5. **资损级重复流水（REQ-07）**：`fund.flow.duplicate` 近 5m > 1（即 2 次及以上）
6. **支付回调超时/对账缺口（REQ-06）**：`pay.callback.timeout` 近 5m > 3

## 运行方式

```bash
# 一键拉起（应用镜像需先 docker build，并接入真实 MySQL）
docker compose -f deploy/observability/docker-compose.yml up -d

# 访问
#   Grafana:    http://localhost:3000  (admin/admin)
#   Prometheus: http://localhost:9090
#   Alertmanager: http://localhost:9093
```

> 若仅需「零依赖」模式（不引入 Micrometer/Prometheus），应用默认仍以 `ObservabilityController`
> 暴露 `/actuator/health` 与 `/actuator/metrics`（自定义 JSON），满足最小可观测性；REQ-01（Prometheus
> 原生抓取）经 `-Pmicrometer` 门控，详见 `scripts/observability/enable-prometheus.sh`。

## 脚本

见 `scripts/observability/`：`up.sh` / `down.sh` / `smoke-test.sh` / `build-check.sh` /
`alert-selfcheck.sh` / `enable-prometheus.sh`。
