# ITER-F08 联调与压测方案（G3 QPS / 风控拦截准确率）

> 关联：PRD-P2-F08-营销玩法扩展.md（G3 门禁见第二节/第七节，峰值验证留压测环境见第十一节）
> 状态：联调已闭环（集成测试 #541 全绿）；风控准确率已真跑达标（#542 precision 90.91%）；G3 真并发压测脚本与方案已就绪，真并发结论留压测环境闭环。

---

## 一、验收门禁（来自 PRD）

| 指标 | 口径 | 门禁 | 本环境结论 |
|---|---|---|---|
| **G3 峰值 QPS** | 秒杀峰值承载 | ≥ 现有拼团峰值的 **3 倍**（Redis 预扣 + MQ 异步落库） | 真并发留压测环境；沙箱指示性见第四节 |
| **风控拦截准确率** | 拦截中确为异常的占比（precision） | ≥ **85%** | **90.91%**（真跑达成，#542） |
| 不超卖 / 不重复发放 | 实际发放 / 应发放 − 1 | 0 | 集成测试 #541 闭环（强一致 + 预扣 + 重放幂等） |

### G3 公式
```
G3_TARGET_QPS = 3 × GROUPON_PEAK_QPS
```
- `GROUPON_PEAK_QPS`：现有拼团/秒杀（F-13.3）在压测环境实测的峰值 QPS，作为门禁输入。
- 主承压路径 = **Redis 预扣 + RocketMQ 异步落库**（`LiveSeckillService` + `SeckillAsyncDispatcher`）。
- 兜底路径 = 缓存不可用时回落 **DB 强一致原子扣减**（不超卖、性能下降）。

---

## 二、联调与压测场景设计

### 场景 A：Redis 预扣路径（G3 主场景，并发）
1. 运营端 `publish` 秒杀活动（DRAFT→ONGOING），内核预热 `act:stock:{activityId}` 至 Redis。
2. k6 以 `ramping-vus` 爬坡至 `targetVUs = ceil(G3_TARGET_QPS / RPS_PER_VU)`，持续承压 `SUSTAIN_SECONDS`。
3. 预扣成功（`ACCEPTED`）的请求异步 `settle` 落库；库存耗尽者正常返回 `LIVE_SECKILL_PREEMPTED`。
4. 门禁读取：`http_reqs` 实际速率 ≥ `G3_TARGET_QPS`、p95<500ms、p99<1000ms、基础设施错误率<1%。

### 场景 B：DB 强一致兜底路径（不预热）
- 不调用 `publish`，直接并发 `grab` → 走 `UPDATE ... SET stock=stock-n WHERE stock>=n` 原子扣减。
- 验证：成功落库数 == 库存、DB 库存终值 == 0、其余被库存网关拒绝（#541 ①）。

### 场景 C：风控协同（高并发下拦截）
- 高并发抢购中注入黑名单设备 / 多账号群控样本，验证 `DefaultActivityRiskGate` 仍按 userId 聚合拦截高危事件，precision 维持 ≥85%（#542 已真跑）。

### 场景 D：幂等与重放（最终一致）
- 同一 `requestId` 重复抢购仅生效一次（#541 ②）；`SeckillSettleCommand` 重放不重复扣减/不新增记录（#541 ③）。

---

## 三、测试资产

| 资产 | 路径 / 类 | 覆盖 | 结果 |
|---|---|---|---|
| 联调集成测试 | `src/test/.../marketing/activity/MarketingIntegrationTest.java` | ①强一致不超卖 ②幂等 ③预扣+异步最终一致 ④砍价成团 ⑤抽奖防超发 ⑥对账一致 ⑦吞吐探针 | **7 例全绿**（#541） |
| 风控准确率验收 | `src/test/.../marketing/activity/RiskControlAccuracyTest.java` | 黑名单/高危开放事件/多账号/可信设备/借黑设备 194 样本 | **precision 90.91% / recall 88.24%**（#542） |
| G3 压测脚本 | `scripts/loadtest/marketing-seckill-k6.js` | 并发吞吐 / 延迟 / 错误率（G3 门禁自动推导） | 待压测环境运行 |
| 联调缺陷修复 | `DefaultActivityRiskGate.allow(...)` | 原按 `bizType="activity_join"` 过滤导致**永不拦截**；改为按 userId + level=high + status=open + 5s 内聚合 | 已修复并验证 |

> **关键修复**：`DefaultActivityRiskGate` 原以 `bizType="activity_join"` 过滤风控事件，但 `RiskEngine` 实际落库事件的 `bizType` 为 `device/user/order`，导致风控闸门**永远不拦截**，风控准确率指标不可达成。已改为按 `userId` 聚合查询（5s 窗口内 high+open），修复后 #542 实证 precision 90.91%。

---

## 四、沙箱指示性结论（非 G3 压测结论）

| 项目 | 数值 | 说明 |
|---|---|---|
| 服务层单线程同步 `grab`（DB 强一致路径） | **≈ 32.8 QPS** | 2000 笔 / 61050ms，沙箱 MySQL 写受限、冷启动；仅指示性 |
| 风控拦截准确率 | **90.91%** | 194 样本（TP60/FP6/FN8/TN120），≥85% 达标 |
| 不超卖 | 已闭环 | #541 强一致/预扣/重放三类断言全绿 |

> 沙箱（CPU/MySQL 受限、ES 降级）**不具备**真实高并发验证条件（PRD 第十一节已明确）。G3 真并发结论（Redis 预扣路径下的峰值承载）须在压测环境以 k6 跑出。

---

## 五、G3 真并发运行手册（压测环境）

### 前置
1. 部署后端（MySQL / Redis 可用，RocketMQ 已接入；ES 可降级）。
2. 运营端创建并 `publish` 一个秒杀活动（库存建议 ≥ `G3_TARGET_QPS × 承压秒数 × 命中率`，避免过早售罄截断压测）。
3. 获取压测账号 Bearer 令牌（建议账号池，避免单 token 限流干扰）；记录 `ACTIVITY_ID`。
4. 实测现有拼团峰值 `GROUPON_PEAK_QPS`（压测环境同口径施压 F-13.3 拼团接口）。

### 执行
```bash
# 安装 k6（压测环境）
brew install k6        # 或见 https://grafana.com/docs/k6/latest/set-up/install/

# 运行 G3 秒杀压测（G3_TARGET = 3 × GROUPON_PEAK_QPS，脚本自动推导）
GROUPON_PEAK_QPS=<拼团峰值> \
ACTIVITY_ID=<已发布秒杀活动ID> \
AUTH_TOKEN=<压测Bearer令牌> \
BASE_URL=http://<压测后端>:8080 \
RPS_PER_VU=50 \
SUSTAIN_SECONDS=120 \
k6 run scripts/loadtest/marketing-seckill-k6.js
```

### 判定（全部满足方为 G3 闭环）
- [ ] `http_reqs` 实测速率 ≥ `G3_TARGET_QPS`（= 3 × GROUPON_PEAK_QPS）
- [ ] `http_req_duration` p95 < 500ms 且 p99 < 1000ms
- [ ] 基础设施错误率（`seckill_infra_error` / `http_req_failed`） < 1%
- [ ] 压测后核对：DB `t_marketing_activity.stock` 终值 == 0、`t_activity_record` 记录数 == 初始库存（不超卖，与 #541 口径一致）
- [ ] 风控拦截 precision 在注入样本中维持 ≥ 85%

---

## 六、闭环结论

- **联调**：#541 七场景全绿，覆盖强一致/预扣/幂等/砍价/抽奖/对账/吞吐；联调缺陷（风控闸门永不拦截）已修复。
- **风控准确率**：#542 真跑 **90.91% ≥ 85%**，达标。
- **G3 QPS**：门禁公式与压测脚本/方案已交付；**真并发峰值结论留压测环境以 k6 闭环**（沙箱仅提供指示性 32.8 QPS，不替代 G3 结论）。
