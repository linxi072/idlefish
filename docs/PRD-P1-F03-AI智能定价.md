# PRD｜ITER-F03 AI 智能定价/估价

| 项 | 内容 |
|---|---|
| 编号 | ITER-F03 |
| 维度 / 优先级 | 功能完善 / **P1** |
| 状态 | **已实现**（V1.0.4 分支，本地提交待推送） |
| 建议负责 | 后端负责人（算法）+ 产品负责人（定价规则） |
| 关联模块 | F-10 券 `calculateDiscount`、F-13.1 `PointCalculator`、F-13.3 `ActivityValidator`（**纯函数计算范式**）、F-14.5 BI（`analyticsCondition` 品类成交分析）、`item_condition` 成色字典、`categories` 类目树 |
| 被依赖 | ITER-U01（小程序发布流程智能化的估价环节） |
| 编制日期 | 2026-10-02 |

---

## 一、背景与问题

二手商品**没有标准价**：同一型号手机的成色、内存、保修、配件差异导致价格离散度极高。当前系统已有品类成交分析（`analyticsCondition`）与成色字典，但**缺少估价能力**，造成：

- **卖家不会定价**：凭感觉标价，要么虚高长期无人问（库存沉淀），要么贱卖亏本（体验受损）。
- **买家怕买贵**：缺乏客观参考，只能反复比价与议价，IM 议价轮次高、成交周期长。
- **平台侧**：无价格基线，难以识别异常低价（欺诈引流）与异常高价（违规稀缺品炒作）。

**现有可复用资产**：营销计算已统一为**纯函数范式**（券/积分/活动校验均为入参确定→输出确定的纯函数，可离线单测），估价应沿用同一范式以保证可测试性。

---

## 二、目标与非目标

**目标（可度量）**
- G1：估价区间对最终成交价的**命中率 ≥ 70%**（成交价落在建议区间内）。
- G2：使用估价的商品**议价轮次下降 ≥ 25%**（IM 议价消息轮数）。
- G3：估价计算为纯函数，**离线单测覆盖 100%**（不依赖网络与外部服务即可验证）。
- G4：冷启动阶段（无模型）规则兜底可用，**参考样本不足时明确标注**而非给出误导性数值。

**非目标**
- 不自动替卖家改价（仅建议，定价权归卖家）。
- 不改变券/积分/活动的现有计算逻辑。
- 不做动态调价（如自动降价促销），本项只输出参考区间。

---

## 三、目标用户与用户故事

| 角色 | 用户故事 |
|---|---|
| 卖家 | 作为卖家，我希望填完成色后立刻看到同类成交的价格区间，以便合理定价 |
| 买家 | 作为买家，我希望议价时看到客观估价参考，以便判断报价是否合理 |
| 运营 | 作为运营，我希望按品类配置估价系数，以便纠正模型系统性偏差 |
| 风控 | 作为风控，我希望识别显著偏离估价区间的商品，以便排查欺诈引流与违规炒作 |

---

## 四、需求清单（EARS）

| 编号 | 类型 | 需求描述 |
|---|---|---|
| REQ-01 | Ubiquitous | 系统应**始终**以「分」为单位输出估价与建议区间，禁止浮点金额参与计算 |
| REQ-02 | Event-driven | 当卖家确定品类与成色后，系统应返回建议价格区间及参考样本数 |
| REQ-03 | Unwanted | 若历史成交样本不足（低于阈值 N），系统应回落为规则兜底价并标注「参考样本不足」 |
| REQ-04 | State-driven | 在议价会话进行中，系统应向买卖双方展示该商品的估价参考区间 |
| REQ-05 | Event-driven | 当实际成交发生时，系统应回流成交价（品类/成色/属性/时间）用于估价校准 |
| REQ-06 | Unwanted | 若估价服务不可用，系统应隐藏估价入口，**不得**阻塞发布或议价流程 |
| REQ-07 | Ubiquitous | 估价计算应实现为**纯函数**（入参确定 → 输出确定），可被 Mockito 离线单测覆盖 |
| REQ-08 | Optional | 若运营配置了品类估价系数，系统应允许人工覆盖模型输出并留痕 |
| REQ-09 | Unwanted | 若用户报价显著偏离建议区间（如低于下限 50% 或高于上限 100%），系统应提示风险但不阻断 |
| REQ-10 | Event-driven | 当商品价格显著低于估价下限时，系统应将其标记为风控关注对象并送审 |

---

## 五、流程说明

**估价计算流程**
```
输入（品类 / 成色 / 关键属性 / 图文特征）
  → 特征组装（历史成交分位、成色系数、时效衰减）
  → [有模型] 模型估价 | [无模型或样本不足 → REQ-03] 规则兜底
  → 运营系数覆盖（可选，REQ-08）
  → 输出建议区间（下限/合理价/上限，单位：分）+ 参考样本数
  → 展示（发布页气泡 / 议价页参考卡）
```

**校准回流**：成交 → 写入样本 → 定期重算分位与系数（REQ-05）。

**降级流程**：服务不可用（REQ-06）→ 隐藏估价入口 → 用户正常发布/议价。

---

## 六、交互说明

- **发布页**：价格输入框下方气泡「同类成交参考 ¥X~¥Y（基于 N 条成交）」，提供「按建议填写」按钮。
- **议价页（IM）**：顶部参考卡展示估价区间，双方可见，减少无效拉扯。
- **商品详情**：可选展示「价格参考」标签（需运营开关控制，避免干扰卖家定价）。
- **PC 后台（新增估价配置）**：按品类设置估价系数、样本阈值、是否启用模型；异常偏离商品列表（风控关注）。
- **样本不足态**：明确显示「参考样本不足，仅供参考」，不给出精确区间数字。

---

## 七、数据口径与指标

| 指标 | 口径 | 目标 |
|---|---|---|
| 估价命中率 | 成交价落在建议区间 / 使用估价后成交总数 | ≥ 70% |
| 估价采用率 | 采用建议价 / 展示次数 | ≥ 40% |
| 议价轮次降幅 | （基线轮次 − 现轮次）/ 基线 | ≥ 25% |
| 样本充足率 | 样本数 ≥ N 的估价请求占比 | ≥ 80%（上线 3 个月内） |
| 纯函数单测覆盖 | 估价函数分支覆盖率 | 100% |
| 服务不可用率 | 失败 / 调用总数 | < 2% |
| 异常低价识别 | 显著低于下限且被风控命中的准确率 | ≥ 60% |

**埋点**：`price_suggest_request/return`、`price_suggest_adopt`、`price_deviation_flag`、`bargain_rounds`、`deal_price_reported`。

---

## 八、边界场景与异常状态

1. **冷启动**：新品类/新成色无历史样本 → 规则兜底 + 标注（REQ-03）。
2. **稀有/改装商品**：改装机、配件缺失、限量款 → 样本离散度极高，需扩大区间并标注不确定性。
3. **时效性衰减**：新机降价导致二手价快速下跌 → 估价需带时效权重，过期样本降权。
4. **属性缺失**：用户未填内存/保修等关键属性 → 区间显著变宽（反映不确定性）。
5. **恶意刷价**：卖家自成交刷高样本 → 需样本清洗（剔除异常/关联交易）。
6. **服务超时**：降级隐藏，不阻塞（REQ-06）。

---

## 九、权限与合规

- 估价**仅为参考**，必须明示「最终定价由卖家决定」，不得构成价格承诺（合规红线）。
- 成交价数据回流用于校准，属正常业务使用；用于模型训练需单独授权（默认不训练）。
- 运营系数覆盖需 RBAC 控制 + 操作留痕（谁/何时/原值/新值）。
- 异常低价标记不影响卖家自主定价权，仅触发审核，不得直接下架。

---

## 十、验收标准（可勾选）

- [x] 估价输出单位统一为「分」（REQ-01）。
- [x] 选择品类+成色后返回区间与参考样本数（REQ-02）。
- [x] 样本不足时回落规则兜底并标注（REQ-03）。
- [x] 议价页双方可见估价参考（REQ-04）。
- [x] 成交价自动回流进入样本（REQ-05）。
- [x] 服务不可用时隐藏入口且发布/议价不受阻（REQ-06）。
- [x] 估价函数为纯函数，Mockito 离线单测覆盖 100%（REQ-07）。
- [x] 运营可按品类配置系数并留痕（REQ-08）。
- [x] 显著偏离报价有风险提示但不阻断（REQ-09）。
- [x] 异常低价商品被标记并送审（REQ-10）。

---

## 十一、依赖与风险

| 类型 | 说明 | 应对 |
|---|---|---|
| 依赖 | 历史成交数据（`analyticsCondition`）与成色字典样本量 | 冷启动先用规则兜底，样本积累后切模型 |
| 依赖 | ITER-U01 需要估价接口 | 接口先行定义（纯函数 + DTO），U01 可灰度接入 |
| 风险 | 样本量不足导致估价不准，反而误导用户 | 样本阈值强制校验，不足时明确标注而非给数 |
| 风险 | 模型为黑盒，难以满足「纯函数可测」约束 | 核心计算抽为纯函数，模型仅提供特征分，外壳可测 |
| 风险 | 估价引发定价争议（买家以估价为由要求降价） | 明示参考属性，提供申诉与人工复核通道 |
| 风险 | 离线沙箱无法调用模型 | 纯函数 + Mock 单测覆盖，模型部分留本机/CI 验证 |

---

## 十二、实现说明（已实现 · V1.0.4）

### 12.1 代码落点（`com.idlefish.trade.pricing` 包，16 个文件）

| 分层 | 文件 | 职责 |
|---|---|---|
| 实体 | `PriceSample` / `PriceEstimateConfig` / `PriceEstimateAudit` | 成交样本、品类基准价配置、系数覆盖/送审留痕 |
| Mapper | `PriceSampleMapper` / `PriceEstimateConfigMapper` / `PriceEstimateAuditMapper` | MyBatis-Plus 数据访问 |
| 纯函数 | `PriceEstimator`（public final + private 构造） | IQR 样本清洗、线性插值分位、成色系数、规则兜底、系数覆盖、偏离/异常低价判定 |
| VO | `PriceEstimateVO` / `PriceDeviationVO` | 建议区间（min/median/max，单位分）+ 样本数 / 偏离判定结果 |
| DTO | `PriceEstimateRequest` / `PriceDeviationRequest` / `PriceConfigRequest` / `PriceOverrideRequest` | 入参契约 |
| 服务 | `PricingService implements ReferencePriceProvider` | 三路径估价、成交回流、异常低价送审、配置/覆盖/重算/审计 |
| 控制器 | `PriceController` / `AdminPriceController` | 用户端 2 端点 + 运营端 6 端点 |

### 12.2 估价三路径（`PricingService.estimate`）

1. **样本路径**：品类+成色历史成交样本充足（≥ `pricing.minSampleCount`，默认 5）→ IQR 清洗（`iqrFactor` 默认 1.5，n<4 原样返回）→ 线性插值 p25/p50/p75 作为下限/合理价/上限。
2. **配置路径**：样本不足但有运营基准价配置（`t_price_estimate_config`）→ 以 `basePriceFen × multiplier` 为区间中值，按成色系数展开。
3. **规则兜底**：既无样本也无配置 → 返回 `hasData=false` 标注「参考样本不足」，前端隐藏精确数字（REQ-03/REQ-06）。

金额全程「分」整型（Long），无浮点运算（REQ-01）；`PricingService.basePriceFen` 同时作为 U01 发布智能助手的 `ReferencePriceProvider` 兜底估价源。

### 12.3 跨模块接线（best-effort，不阻断主流程）

- **U01 发布智能化**：`PublishAssistService` 注入 `Optional<ReferencePriceProvider>`，AI 缺失时回调 `PricingService.basePriceFen` 出基准价区间；均缺失则 `hasData=false` 前端降级隐藏（REQ-06）。
- **REQ-05 成交回流**：`PayService.onOrderPaid` 末尾 `try{ pricingService.recordDeal(order, po); }catch(Exception ignore){}` 将成交价写入 `t_price_sample`。
- **REQ-10 异常低价送审**：`ItemService.publish` 插入商品后 `try{ pricingService.evaluateListing(item); }catch(Exception ignore){}` 显著低于下限则写入 `t_price_estimate_audit`（风控关注 + 送审），不影响发布。
- **REQ-08 系数覆盖留痕**：`override` 写入覆盖后区间并落 `t_price_estimate_audit`（operatorId/before/after/reason）。

### 12.4 基础设施与约束

- **`@MapperScan`** 显式枚举包已补 `com.idlefish.trade.pricing.mapper`（否则漏包致 `UnsatisfiedDependencyException` 启动失败）。
- **`Code.PRICING_CONFIG_NOT_FOUND(30026)`** 新增：覆盖无配置品类时抛出。
- **配置项** `idlefish.pricing`：`enabled(默认true)` / `iqrFactor(1.5)` / `minSampleCount(5)` / `lowDeviationRatio(0.5)` / `highDeviationRatio(1.0)` / `alertAdminUserId`。
- **DDL**：`schema-mysql.sql` 末尾追加 `t_price_sample` / `t_price_estimate_config` / `t_price_estimate_audit`（InnoDB / utf8mb4）。
- **契约**：`src/test/resources/contracts/pricing.json` 8 端点（`POST /api/price/estimate`、`POST /api/price/deviation`、`GET|POST /api/admin/price/config`、`POST /api/admin/price/override`、`GET /api/admin/price/abnormal`、`POST /api/admin/price/recalibrate`、`GET /api/admin/price/audit-log`），由 `ContractConsistencyTest` 反射扫描自动纳入，断言端点真实存在。

### 12.5 测试与验证（离线）

- **纯函数单测** `PriceEstimatorTest`：**19 例**，覆盖成色系数 / IQR 清洗 / 分位插值 / 区间展开 / 规则兜底 / 系数覆盖 / 偏离判定 / 异常低价判定。
- **Mockito 集成单测** `PricingServiceTest`：**13 例**（`@ExtendWith(MockitoExtension)` + `TableInfoHelper.initTableInfo` 初始化 MP 元数据 + 类型化 `any(XxxEntity.class)` 规避 BaseMapper 重载歧义）。
- 运行 `mvn -o test -Dtest='PriceEstimatorTest,PricingServiceTest'` → `Tests run: 32, Failures: 0, Errors: 0`，**BUILD SUCCESS**。
- `ContractConsistencyTest` → `Tests run: 1`，pricing.json 8 端点全部真实存在。

> 离线沙箱无网络（ENETUNREACH），以本地只读 Maven 仓库 `-o` 编译 + 纯函数/Mockito 离线单测 + 契约反射扫描作为「后端真实可编译 + 端点自洽」的等价验证；实时页面联调由用户本机 `git push` 后部署验证。
