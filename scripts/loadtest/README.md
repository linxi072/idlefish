# 全链路压测编排（F-15.4）

统一调度既有真机联调脚本，把「IM 实时收发」与「支付回流」两类压测汇总为一份报告。

## 脚本

| 脚本 | 职责 |
|---|---|
| `scripts/loadtest/run-all.js` | 编排器：调度下面两个脚本并汇总 `loadtest-report.json` |
| `scripts/im-loadtest.js` | IM 消息送达率 / 延迟（P50/P95/Max）压测（F-10 真机联调脚本） |
| `scripts/pay-callback-sim.js` | 支付回流链路压测：幂等、金额校验、状态同步（F-10 真机联调脚本） |

> 压测逻辑与测量口径由各自脚本持有，编排器只负责调度与汇总，避免逻辑重复。

## 用法

```bash
# 离线（无需后端）：仅校验脚本与测量口径不崩，exit=0 即通过
node scripts/loadtest/run-all.js --mock

# 真机 / 本地后端联调
node scripts/loadtest/run-all.js --live http://localhost:8080 --rounds 200 --users 4
```

参数：

- `--mock`：离线模式（默认），不连接后端。
- `--live <url>`：对运行中的后端压测。
- `--rounds`：IM 轮次（默认 100）。
- `--users`：IM 并发用户数（默认 2）。
- `--amount`：支付回流金额（分，默认 138000 = ¥1380.00）。
- `--out`：报告路径（默认 `loadtest-report.json`）。

## 报告字段（`loadtest-report.json`）

```json
{
  "tool": "idlefish-loadtest-orchestrator",
  "mode": "mock | live",
  "target": null | "http://localhost:8080",
  "params": { "rounds": 100, "users": 2, "amount": 138000 },
  "results": {
    "im":  { "script": "scripts/im-loadtest.js", "exit": 0, "summary": "..." },
    "pay": { "script": "scripts/pay-callback-sim.js", "exit": 0, "summary": "..." }
  },
  "status": "PASS | FAIL"
}
```

## 验收口径

- 编排器 `exit=0` ⇔ 两个子脚本均 `exit=0`（无崩溃、无未捕获异常）。
- `live` 模式下关注 `im` 的送达率与 P95 延迟、`pay` 的幂等与金额校验是否通过（见子脚本 stdout 明细）。
- 多实例部署下，建议配合 F-15.2 分布式限流观测配额是否准确（不超发、不漏放）。
