# PC 运营后台 UI 全面优化方案（Vite / Vue3 / Element Plus）

> 范围：仅改前端视觉与交互，**不动业务、接口、表结构**，保持现有选项式 API + `global.css` 结构一致。
> 现状代码基线：`App.vue` / `global.css` / `Login.vue` / `Dashboard.vue` / `Users.vue` / `Items.vue` / `Wallet.vue` / `Marketing.vue` 等。

---

## 一、当前可改进的具体问题（按严重度）

| # | 问题 | 证据（代码位置） | 影响 |
|---|------|----------------|------|
| P0-1 | **列表页缺少分页控件** | `Users.vue`、`Items.vue` 都 `await ...users({page:1,size:20})` 取回 `total` 却**从不渲染 `el-pagination`**；`total` 变量被赋值但弃用 | 数据超过 20 条无法翻页，功能与体验双重缺陷 |
| P0-2 | **销毁性操作无二次确认不一致** | `Users.vue` 的「封禁/解封」直接 `toggleBan` **无 `$confirm`**；而 `Wallet.vue` 提现审批有确认 | 高危操作误触无兜底，且全站交互不统一 |
| P0-3 | **侧边栏用 Emoji 作图标** | `App.vue` `menus` 数组：`📊🛍️⭐📦⚖️👤🗂️🛡️📈🎯🏅🤖🤝🔍💰🏷️🎟️🎪🔔⚙️👨‍💼🔑🏢📑📚` | Emoji 在各 OS 渲染不一致（Windows/Android 偏彩色、macOS 偏单色），显小、不专业、与设计语言割裂 |
| P1-1 | **页面头部样式不统一** | `Users/Items/Wallet` 直接 `<h2 class="page-title">`；`Marketing.vue` 包了 `.page-head`（**但 `global.css` 并未定义 `.page-head`**，等于裸 div）；`Dashboard` 在 `v-loading` 内 | 头部没有统一「标题+描述+面包屑+操作区」结构，间距/层级随页面漂移 |
| P1-2 | **主色与品牌色冲突** | 品牌是黄/橙（`#FFCE3D/#FFB300`），但所有 `el-button type="primary"` 用的是 Element Plus **默认蓝 `#409EFF`** | 品牌识别弱、视觉语言分裂；黄色仅出现在登录渐变与侧边栏选中态 |
| P1-3 | **无设计令牌（Design Tokens）** | `global.css` 全部硬编码 hex：背景 `#F4F5F7`、文字 `#1A1A1A/#666/#9A9A9A`、间距 `22/24/36/40px`、圆角 `12/16px`、阴影三套写法 | 难以维护，配色/间距/层级无统一标尺，后续迭代必然继续漂移 |
| P1-4 | **字体排版无比例** | `.page-title` 20px/700、`.tb-title` 18px/700、`.stat-num` 30px、`.stat-label` 13px、`.login-brand` 24px——尺寸/字重随用随写 | 信息层级不清晰，标题/正文/辅助文字区分度不足 |
| P1-5 | **响应式缺失** | `.stat-grid { grid-template-columns: repeat(3,1fr) }` 固定 3 列；`Wallet.vue` 用 `el-col :span="4"` 固定 6 列；全仓**零 media query** | 笔记本 1366 宽或缩放后拥挤、溢出；对账卡片在小屏挤压 |
| P1-6 | **空状态/加载态不统一** | 有的用 `v-loading`，有的手写 `.empty-tip` div，有的（`Users/Items` 无数据）连空态都没有 | 体验割裂，空白表格区无引导 |
| P2-1 | **筛选栏不规范** | `el-form inline` 无「重置」按钮；`el-select` 内联 `style="width:140px"`；按钮文案不统一（「查询」vs「刷新」vs「查询」） | 操作路径不标准，筛选条件多了之后难用 |
| P2-2 | **表格列无对齐/无吸顶** | 金额列未统一右对齐；`el-table` 未开 `:max-height`/吸顶；`fixed="right"` 操作列宽度写死 | 长表格滚动时表头丢失、数字列对不齐，扫读成本高 |
| P2-3 | **登录页可更现代** | 全屏渐变 + 居中卡片，无品牌侧栏、无安全说明、`prefix-icon="User"/"Lock"` 因未注册图标组件**实际不显示图标** | 偏朴素，且图标缺失是隐性 bug |
| P2-4 | **顶栏信息单薄** | `App.vue` 顶栏仅：铃铛 + 用户名文字 + 退出；无头像、无面包屑、无侧边栏折叠、无全屏/主题 | 缺少后台常见的导航上下文与快捷操作 |

---

## 二、设计系统重构方案（Design Tokens）

在 `src/styles/global.css` 顶部建立 `:root` 令牌，后续所有样式改为引用变量（**不引入 Tailwind/SCSS**，沿用现有纯 CSS，降低侵入**）。

### 2.1 色彩令牌
```css
:root {
  /* 品牌（闲鱼黄→更沉稳的琥珀，避免荧光感） */
  --brand-1: #FFB300;          /* 主品牌色 */
  --brand-2: #FFCE3D;          /* 浅品牌/渐变副色 */
  --brand-ink: #5A4000;        /* 品牌色上的文字（深棕，保证对比度） */
  /* 中性 */
  --bg: #F4F5F7;               /* 页面底色 */
  --surface: #FFFFFF;          /* 卡片/弹窗 */
  --border: #ECEDEF;           /* 描边 */
  --text-1: #1A1A1A;          /* 主文字 */
  --text-2: #5A5F66;          /* 次级文字 */
  --text-3: #9A9FA8;          /* 辅助/占位 */
  /* 语义（覆盖 Element Plus 默认蓝，统一为暖色体系） */
  --el-color-primary: #F5731F; /* 主操作色：暖橙，比纯黄更克制、对比更好 */
  --el-color-success: #18A058;
  --el-color-warning: #FF8F1F;
  --el-color-danger:  #F53F3F;
  --el-color-info:    #86909C;
}
```
> 说明：把 `--el-color-primary` 从默认蓝改为暖橙，**全站所有 `type="primary"` 按钮/链接自动统一为品牌色系**，一举解决 P1-2。按钮文字保持 Element 默认白字，因橙底白字对比达标（黄底才需改深字）。

### 2.2 字体排版比例（type scale）
```css
:root{
  --fs-display: 28px;  --fs-h1: 22px;  --fs-h2: 18px;  --fs-body: 14px;
  --fs-sm: 13px;       --fs-xs: 12px;
  --lh-tight: 1.3;     --lh-base: 1.6;
  --fw-regular: 400;   --fw-medium: 500; --fw-bold: 700;
}
.page-title{ font-size: var(--fs-h1); font-weight: var(--fw-bold); line-height: var(--lh-tight); color: var(--text-1); }
```
统一：页面标题 22px、卡片标题 18px、正文 14px、辅助 12–13px。

### 2.3 间距 / 圆角 / 阴影 / 层级
```css
:root{
  --sp-1:4px; --sp-2:8px; --sp-3:12px; --sp-4:16px; --sp-5:20px; --sp-6:24px; --sp-8:32px;
  --radius-sm:6px; --radius-md:10px; --radius-lg:16px;
  --shadow-1: 0 1px 2px rgba(0,0,0,.04), 0 2px 8px rgba(0,0,0,.04);  /* 卡片 */
  --shadow-2: 0 8px 24px rgba(0,0,0,.08);                              /* 弹窗/悬浮 */
}
```
- 内容区内边距统一 `--sp-6`(24px)；卡片内边距 `--sp-5`(20px)。
- `.stat-grid` 改为 `repeat(auto-fill, minmax(200px,1fr))` 自适应列数（修 P1-5）。

---

## 三、组件级优化与实现建议

### 3.1 统一页面头部（新增 `src/components/PageHeader.vue`）
消除 P1-1：所有页面用同一组件，支持「标题 + 描述 + 面包屑 + 右侧操作插槽」。
```vue
<!-- src/components/PageHeader.vue -->
<template>
  <header class="ph">
    <div class="ph__main">
      <el-breadcrumb v-if="crumbs?.length" class="ph__crumbs" separator="/">
        <el-breadcrumb-item v-for="c in crumbs" :key="c">{{ c }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="ph__title">{{ title }}</h2>
      <p v-if="desc" class="ph__desc">{{ desc }}</p>
    </div>
    <div class="ph__extra"><slot name="extra" /></div>
  </header>
</template>
<script>
export default { name:'PageHeader', props:{ title:String, desc:String, crumbs:Array } }
</script>
<style scoped>
.ph{ display:flex; align-items:flex-end; justify-content:space-between; margin-bottom:var(--sp-5); gap:var(--sp-4); }
.ph__title{ font-size:var(--fs-h1); font-weight:var(--fw-bold); margin:0; color:var(--text-1); }
.ph__crumbs{ margin-bottom:var(--sp-2); }
.ph__desc{ font-size:var(--fs-sm); color:var(--text-3); margin:var(--sp-1) 0 0; }
</style>
```
落地：`Users.vue` 等把 `<h2 class="page-title">用户管理</h2>` 换成
```vue
<page-header title="用户管理" desc="查看与封禁违规用户" :crumbs="['用户','用户管理']">
  <template #extra><el-button type="primary" @click="load">刷新</el-button></template>
</page-header>
```
并在 `main.js` 全局注册 `app.component('PageHeader', PageHeader)`（或局部 import）。

### 3.2 侧边栏图标：Emoji → Element Plus 图标（修 P0-3）
1. 安装图标库（加入 `package.json` 依赖）：`npm i @element-plus/icons-vue`
2. `main.js` 全局注册：
```js
import * as ElIcons from '@element-plus/icons-vue';
for (const [k,v] of Object.entries(ElIcons)) app.component(k, v);
```
3. `App.vue` 菜单数据用组件名替代 emoji，模板改用 `<component :is="m.icon">`：
```js
menus: [
  { key:'dashboard', label:'控制台', icon:'DataLine' },
  { key:'items',     label:'商品审核', icon:'Goods' },
  ...
]
```
模板：`<el-icon><component :is="m.icon"/></el-icon>`（替换 `<span class="m-icon">{{ m.icon }}</span>`）。

### 3.3 列表页补充分页（修 P0-1）
`Users.vue` / `Items.vue` 在 `el-table` 下加：
```vue
<el-pagination class="mt-4" layout="total, sizes, prev, pager, next, jumper"
  :total="total" :page-size="size" :current-page="page"
  @current-change="p=>{page=p;load()}" @size-change="s=>{size=s;load()}" />
```
并让 `load()` 使用 `this.page/this.size`。建议把分页逻辑抽进 `listPageMixin`（已存在）以全站复用。

### 3.4 高危操作统一二次确认（修 P0-2）
`Users.vue` 的 `toggleBan` 增加：
```js
async toggleBan(row){
  const ban = row.status === 0;
  try { await this.$confirm(`确认${ban?'封禁':'解封'}用户「${row.nickname||row.id}」？`, '提示', { type: ban?'warning':'info' }); }
  catch { return; }
  await adminApi.banUser(row.id, ban); ...
}
```
并把「封禁/解封」统一为「危险/默认」配对样式。

### 3.5 筛选栏规范化（修 P2-1）
新增通用筛选样式 + 统一「查询/重置」：
```vue
<el-form inline class="filter-bar">
  <el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="昵称/手机号" style="width:200px"/></el-form-item>
  ...
  <el-form-item>
    <el-button type="primary" @click="load">查询</el-button>
    <el-button @click="reset">重置</el-button>
  </el-form-item>
</el-form>
```
`.filter-bar{ margin-bottom:var(--sp-4) }`，select 宽度统一走 `style="width:160px"` 或令牌类。

### 3.6 表格增强（修 P2-2）
- 金额/数字列加 `align="right"`（已有 `.amount` 红色，但建议金额用 `--text-1` 常规色、仅支出/退款用红）。
- 长列表加 `:max-height="560"` 启用表头吸顶 + 内部滚动。
- 统一空态：表格无数据时渲染 `<el-empty description="暂无数据"/>`（替换手写 `.empty-tip`）。

### 3.7 登录页升级（修 P2-3）
保留居中卡片，但：
- 左/上品牌区放 Logo + 一句话 slogan；
- 表单 `prefix-icon` 改用已注册的 `User`/`Lock` 图标组件（而非字符串），届时图标正常显示；
- 卡片加轻微 `box-shadow: var(--shadow-2)`，标题层级用令牌；
- 演示账号提示改为更克制的 footnote。

### 3.8 顶栏补全（修 P2-4）
`App.vue` 顶栏增加：侧边栏折叠按钮、用户名前加 `el-avatar`（取昵称首字）、面包屑（与 PageHeader 呼应）。折叠态用 `el-menu` 的 `:collapse` 控制侧边栏宽度。

---

## 四、落地步骤与文件清单

| 步骤 | 文件 | 动作 |
|------|------|------|
| 1 | `src/styles/global.css` | 顶部加 `:root` 令牌（色/字/距/圆角/阴影）；重构 `.page-title`、`.stat-grid`、`.card` 等引用令牌；定义 `.filter-bar`、`.page-head` |
| 2 | `package.json` | 增加 `@element-plus/icons-vue` 依赖 |
| 3 | `src/main.js` | 全局注册 Element Plus 图标 + `PageHeader` 组件 |
| 4 | `src/App.vue` | 菜单 emoji→图标组件名；顶栏加折叠/头像/面包屑 |
| 5 | `src/components/PageHeader.vue` | **新增** 统一页面头部 |
| 6 | `src/views/*` | 各列表页接入 `PageHeader`、补 `el-pagination`、补空态、规范筛选栏 |
| 7 | `src/mixins/list-page.js` | （可选）把分页/筛选状态抽进已有 mixin |
| 8 | `src/views/auth/Login.vue` | 图标组件化、卡片阴影/层级令牌化 |

> 所有改动为纯前端视觉/交互层，不触及 `api/`、`mock/`、`utils/format.js` 的字段契约与业务逻辑。

---

## 五、分阶段优先级与节奏

- **P0（必做，含功能缺陷）**：分页补齐、高危操作确认、侧边栏图标替换、`--el-color-primary` 主色统一。
- **P1（体验跃迁）**：Design Tokens 落地、PageHeader 统一、字体比例、响应式、`el-empty` 空态。
- **P2（打磨）**：筛选栏规范、表格对齐/吸顶、登录页与顶栏细节、头像/面包屑。

建议按 P0→P1→P2 分 3 笔提交推进；每阶段后本机 `npm run dev` 走查 3~5 个页面即可。

---

## 六、预期收益
- 视觉：品牌暖橙主色统一、层级清晰、间距规整、全站响应式可用。
- 体验：列表可翻页、危险操作有兜底、空/加载态一致、导航有上下文（面包屑/头像）。
- 可维护性：令牌化后，后续换肤/微调只需改 `:root` 十几行，而非全仓搜 hex。
